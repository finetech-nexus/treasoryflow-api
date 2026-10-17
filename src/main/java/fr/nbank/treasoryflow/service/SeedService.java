package fr.nbank.treasoryflow.service;

import static fr.nbank.treasoryflow.domain.BusinessClock.REPORTING_DATE;

import fr.nbank.treasoryflow.domain.AuditEvent;
import fr.nbank.treasoryflow.domain.BankAccount;
import fr.nbank.treasoryflow.domain.BucketClassification;
import fr.nbank.treasoryflow.domain.CashPool;
import fr.nbank.treasoryflow.domain.CashPoolMember;
import fr.nbank.treasoryflow.domain.CashPosition;
import fr.nbank.treasoryflow.domain.Codes.ApprovalStatus;
import fr.nbank.treasoryflow.domain.Codes.DealStatus;
import fr.nbank.treasoryflow.domain.Codes.FlowClass;
import fr.nbank.treasoryflow.domain.Codes.FormulaCode;
import fr.nbank.treasoryflow.domain.Codes.Jurisdiction;
import fr.nbank.treasoryflow.domain.Codes.LimitAction;
import fr.nbank.treasoryflow.domain.Codes.PaymentPurpose;
import fr.nbank.treasoryflow.domain.Codes.PoolType;
import fr.nbank.treasoryflow.domain.Codes.PositionKind;
import fr.nbank.treasoryflow.domain.Counterparty;
import fr.nbank.treasoryflow.domain.ExposureLimit;
import fr.nbank.treasoryflow.domain.ForecastFlow;
import fr.nbank.treasoryflow.domain.FxRate;
import fr.nbank.treasoryflow.domain.GroupConsolidationScope;
import fr.nbank.treasoryflow.domain.IntercompanyFlow;
import fr.nbank.treasoryflow.domain.LegalEntity;
import fr.nbank.treasoryflow.domain.LiquidityItem;
import fr.nbank.treasoryflow.domain.PaymentOrder;
import fr.nbank.treasoryflow.domain.PublishedIndicator;
import fr.nbank.treasoryflow.domain.RegulatoryChangeLog;
import fr.nbank.treasoryflow.domain.RegulatoryJurisdiction;
import fr.nbank.treasoryflow.domain.RegulatoryParameter;
import fr.nbank.treasoryflow.domain.RegulatoryProfile;
import fr.nbank.treasoryflow.domain.RegulatoryRuleVersion;
import fr.nbank.treasoryflow.domain.ScopeMember;
import fr.nbank.treasoryflow.domain.SourceMovement;
import fr.nbank.treasoryflow.domain.TreasuryDeal;
import fr.nbank.treasoryflow.domain.TreasuryProduct;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeedService implements CommandLineRunner {
    static final String BIAT = "BANQUE INTERNATIONALE ARABE DE TUNISIE — BIAT";
    static final String BIAT_SWIFT = "BIATTNTTXXX";
    static final String BIAT_HQ = "70-72 avenue Habib Bourguiba, 1000 Tunis";
    static final String BIAT_RC = "B 187 88 1996";
    static final String BIAT_WEB = "https://www.biat.com.tn";
    static final String PUBLISHED_SOURCE =
            "États financiers consolidés BIAT au 31 décembre 2025, publiés en milliers de dinars. Montant converti en dinars. Ce n'est pas le scénario de calcul du TMS.";

    private static final String DISCLAIMER =
            "Identité et indicateurs publics BIAT. Les pondérations du scénario restent à valider sur les textes consolidés. Ce profil n'est pas une certification réglementaire.";

    private final TreasuryStore store;

    public SeedService(TreasuryStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (store.find(LegalEntity.class, "TN-BANK").isPresent()) {
            refreshBiatIdentity();
            publishedIndicators();
            cashServices();
            store.flush();
            return;
        }
        jurisdiction("TN", "Banque centrale de Tunisie", "Profil de liquidité et de déclaration propre aux entités tunisiennes.");
        jurisdiction("EU", "BCE et autorité nationale compétente", "LCR, NSFR et reporting prudentiel européen, sans application automatique hors UE.");
        profile("PROF-TN", "TN-BANK", Jurisdiction.TN, "Profil BCT — BIAT");
        profile("PROF-EU", "EU-BANK", Jurisdiction.EU, "Profil CRR");
        entity("TN-BANK", BIAT, "TN", Jurisdiction.TN, "TND", "PROF-TN", true,
                "Réglementation des changes et contrainte de liquidité locale : la trésorerie tunisienne n'est pas librement transférable vers une entité européenne.");
        entity("EU-BANK", "Périmètre européen du scénario", "FR", Jurisdiction.EU, "EUR", "PROF-EU", false, null);
        applyBiatIdentity(store.find(LegalEntity.class, "TN-BANK").orElseThrow());
        account("TN-BCT-TND", "TN-BANK", "CP-BCT", "BCT-TND-001", "TND", "BCT");
        account("TN-NOSTRO-EUR", "TN-BANK", "CP-EU-BANK", "NOSTRO-EUR-001", "EUR", "NOSTRO");
        account("EU-TARGET-EUR", "EU-BANK", "CP-BNPP", "TARGET-EUR-001", "EUR", "TARGET");
        party("CP-BCT", "Banque centrale de Tunisie", "TN");
        party("CP-BNPP", "BNP Paribas", "FR");
        party("CP-EU-BANK", "Périmètre européen du scénario", "FR");
        party("CP-TN-BANK", BIAT, "TN");
        product("FX_SPOT", "Change au comptant", true);
        product("FX_FORWARD", "Change à terme", true);
        product("FX_SWAP", "Swap de change", true);
        product("IB_PLACE", "Placement interbancaire", false);
        product("IB_BORROW", "Emprunt interbancaire", false);
        product("REPO", "Pension livrée", false);
        product("REVERSE_REPO", "Pension livrée inverse", false);
        rate();
        position("POS-TN-BCT-BOOK", "TN-BANK", "TN-BCT-TND", "TND", PositionKind.BOOKED, "500000000.00", null, "CORE");
        position("POS-TN-BCT-AVL", "TN-BANK", "TN-BCT-TND", "TND", PositionKind.AVAILABLE, "500000000.00", null, "CORE");
        position("POS-TN-IC", "TN-BANK", "TN-NOSTRO-EUR", "EUR", PositionKind.AVAILABLE, "1000000.00", "IC-2026-001", "TMS");
        position("POS-EU-CASH-BOOK", "EU-BANK", "EU-TARGET-EUR", "EUR", PositionKind.BOOKED, "5000000.00", null, "CORE");
        position("POS-EU-CASH-AVL", "EU-BANK", "EU-TARGET-EUR", "EUR", PositionKind.AVAILABLE, "5000000.00", null, "CORE");
        position("POS-EU-IC", "EU-BANK", "EU-TARGET-EUR", "EUR", PositionKind.AVAILABLE, "-1000000.00", "IC-2026-001", "TMS");
        scope();
        ruleTn();
        ruleLcr();
        ruleNsfr();
        item("TN-BANK", "TN_LIQUIDITY_STOCK", "ASSET", "CASH_BCT", "TND", "500000000.00", "ACC:TN-BCT-TND");
        item("TN-BANK", "TN_LIQUIDITY_STOCK", "ASSET", "SOVEREIGN_TN", "TND", "200000000.00", "BOOK:BT-TN");
        item("TN-BANK", "TN_LIQUIDITY_STOCK", "ASSET", "IB_PLACEMENT_LE_1M", "TND", "100000000.00", "BOOK:IB-TN");
        item("TN-BANK", "TN_LIQUIDITY_STOCK", "LIABILITY", "DEMAND_DEPOSITS", "TND", "600000000.00", "BOOK:DEP-DEM");
        item("TN-BANK", "TN_LIQUIDITY_STOCK", "LIABILITY", "TERM_DEPOSITS_LE_1M", "TND", "150000000.00", "BOOK:DEP-TERM");
        item("EU-BANK", "EU_LCR", "HQLA", "L1", "EUR", "1000000000.00", "BOOK:L1");
        item("EU-BANK", "EU_LCR", "HQLA", "L2A", "EUR", "200000000.00", "BOOK:L2A");
        item("EU-BANK", "EU_LCR", "HQLA", "L2B", "EUR", "100000000.00", "BOOK:L2B");
        item("EU-BANK", "EU_LCR", "OUTFLOW", "RETAIL_STABLE", "EUR", "400000000.00", "BOOK:RET-STABLE");
        item("EU-BANK", "EU_LCR", "OUTFLOW", "WHOLESALE_FINANCIAL", "EUR", "300000000.00", "BOOK:WH-FIN");
        item("EU-BANK", "EU_LCR", "INFLOW", "INFLOW_FINANCIAL", "EUR", "100000000.00", "BOOK:IN-FIN");
        item("EU-BANK", "EU_NSFR", "ASF", "CAPITAL_GT_1Y", "EUR", "500000000.00", "BOOK:CAP");
        item("EU-BANK", "EU_NSFR", "ASF", "RETAIL_STABLE", "EUR", "400000000.00", "BOOK:RET-STABLE");
        item("EU-BANK", "EU_NSFR", "RSF", "HQLA_L1", "EUR", "1000000000.00", "BOOK:L1");
        item("EU-BANK", "EU_NSFR", "RSF", "LOAN_NF_GT_1Y", "EUR", "600000000.00", "BOOK:LOAN-NF");
        item("EU-BANK", "EU_NSFR", "RSF", "OTHER_ASSETS", "EUR", "100000000.00", "BOOK:OTHER");
        BucketClassification classification = new BucketClassification();
        classification.setId("CLS-TN-CASH");
        classification.setEntityId("TN-BANK");
        classification.setFormulaCode("TN_LIQUIDITY_STOCK");
        classification.setSourceType("ACCOUNT");
        classification.setSourceKey("TN-BCT-TND");
        classification.setBucket("CASH_BCT");
        classification.setValidFrom(LocalDate.of(2014, 12, 16));
        store.insert(classification);
        limit();
        deal();
        flow("FLOW-EU-FUND", "EU-BANK", "EU-TARGET-EUR", "EUR", LocalDate.of(2026, 10, 20), "3000000.00", FlowClass.ESTIMATED, true, "Tirage de financement annulable");
        flow("FLOW-EU-OUT", "EU-BANK", "EU-TARGET-EUR", "EUR", LocalDate.of(2026, 10, 22), "-10000000.00", FlowClass.CONTRACTUAL, false, "Échéance de financement");
        publishedIndicators();
        cashServices();
        movement("MV-MATCH", "REF-EU-MM-1", "7000000.00");
        movement("MV-ORPHAN", "ORPHAN-1", "100.00");
        AuditEvent event = new AuditEvent();
        event.setId("AUD-SEED");
        event.setOccurredAt(Instant.parse("2026-10-17T08:00:00Z"));
        event.setActor("system.compliance");
        event.setAction("SEED");
        event.setSubjectType("LegalEntity");
        event.setSubjectId("GRP-TN-EU");
        event.setDetail("Référentiel BIAT et scénario de calcul Tunisie – Europe");
        store.insert(event);
        store.flush();
    }

    private void refreshBiatIdentity() {
        store.find(LegalEntity.class, "TN-BANK").ifPresent(this::applyBiatIdentity);
        store.find(Counterparty.class, "CP-TN-BANK").ifPresent(party -> party.setName(BIAT));
        store.find(RegulatoryProfile.class, "PROF-TN").ifPresent(profile -> profile.setName("Profil BCT — BIAT"));
    }

    private void applyBiatIdentity(LegalEntity entity) {
        entity.setLegalName(BIAT);
        entity.setSwiftBic(BIAT_SWIFT);
        entity.setHeadquarters(BIAT_HQ);
        entity.setRegistration(BIAT_RC);
        entity.setWebsite(BIAT_WEB);
    }

    private void publishedIndicators() {
        published("PUB-BCT-CASH", "CASH_BCT_CCP_TGT", "Caisse et avoirs auprès de la BCT, CCP et TGT", "387138000.00");
        published("PUB-BCT-BALANCES", "BALANCES_BCT", "Avoirs chez la BCT", "190689000.00");
        published("PUB-BANKS", "DUE_FROM_BANKS", "Créances sur les établissements bancaires et financiers", "5000205000.00");
        published("PUB-CUSTOMERS", "CUSTOMER_LOANS", "Créances sur la clientèle", "13068032000.00");
    }

    private void published(String id, String code, String label, String amount) {
        if (store.find(PublishedIndicator.class, id).isPresent()) {
            return;
        }
        PublishedIndicator row = new PublishedIndicator();
        row.setId(id);
        row.setEntityId("TN-BANK");
        row.setCode(code);
        row.setLabel(label);
        row.setAmount(new BigDecimal(amount));
        row.setCurrency("TND");
        row.setAsOf(LocalDate.of(2025, 12, 31));
        row.setSource(PUBLISHED_SOURCE);
        store.insert(row);
    }

    private void cashServices() {
        if (store.find(CashPool.class, "POOL-EUR").isEmpty()) {
            CashPool pool = new CashPool();
            pool.setId("POOL-EUR");
            pool.setName("Pool EUR physique");
            pool.setCurrency("EUR");
            pool.setHeaderEntityId("EU-BANK");
            pool.setHeaderAccountId("EU-TARGET-EUR");
            pool.setType(PoolType.PHYSICAL);
            pool.setStatus("ACTIVE");
            store.insert(pool);
            poolMember("POOL-EUR-EU", "POOL-EUR", "EU-BANK", "EU-TARGET-EUR");
            poolMember("POOL-EUR-TN", "POOL-EUR", "TN-BANK", "TN-NOSTRO-EUR");
        }
        if (store.find(CashPool.class, "POOL-TND").isEmpty()) {
            CashPool pool = new CashPool();
            pool.setId("POOL-TND");
            pool.setName("Pool TND notionnel BIAT");
            pool.setCurrency("TND");
            pool.setHeaderEntityId("TN-BANK");
            pool.setHeaderAccountId("TN-BCT-TND");
            pool.setType(PoolType.NOTIONAL);
            pool.setStatus("ACTIVE");
            store.insert(pool);
            poolMember("POOL-TND-BCT", "POOL-TND", "TN-BANK", "TN-BCT-TND");
        }
        if (store.find(PaymentOrder.class, "PAY-EU-SUP-1").isEmpty()) {
            PaymentOrder payment = new PaymentOrder();
            payment.setId("PAY-EU-SUP-1");
            payment.setEntityId("EU-BANK");
            payment.setDebitAccountId("EU-TARGET-EUR");
            payment.setBeneficiaryName("Fournisseur européen");
            payment.setCurrency("EUR");
            payment.setAmount(new BigDecimal("250000.00"));
            payment.setValueDate(REPORTING_DATE);
            payment.setPurpose(PaymentPurpose.SUPPLIER);
            payment.setStatus(DealStatus.DRAFT);
            payment.setCreatedBy("system.treasury");
            payment.setCreatedAt(Instant.parse("2026-10-17T08:30:00Z"));
            store.insert(payment);
        }
    }

    private void poolMember(String id, String poolId, String entityId, String accountId) {
        CashPoolMember member = new CashPoolMember();
        member.setId(id);
        member.setPoolId(poolId);
        member.setEntityId(entityId);
        member.setAccountId(accountId);
        store.insert(member);
    }

    private void jurisdiction(String code, String supervisor, String description) {
        RegulatoryJurisdiction row = new RegulatoryJurisdiction();
        row.setCode(code);
        row.setSupervisor(supervisor);
        row.setDescription(description);
        store.insert(row);
    }

    private void profile(String id, String entityId, Jurisdiction jurisdiction, String name) {
        RegulatoryProfile profile = new RegulatoryProfile();
        profile.setId(id);
        profile.setEntityId(entityId);
        profile.setJurisdiction(jurisdiction);
        profile.setName(name);
        profile.setStatus("ACTIVE");
        profile.setDisclaimer(DISCLAIMER);
        store.insert(profile);
    }

    private void entity(String id, String name, String country, Jurisdiction jurisdiction, String currency, String profile,
            boolean restricted, String reason) {
        LegalEntity entity = new LegalEntity();
        entity.setId(id);
        entity.setLegalName(name);
        entity.setCountry(country);
        entity.setJurisdiction(jurisdiction);
        entity.setReportingCurrency(currency);
        entity.setStatus("ACTIVE");
        entity.setValidFrom(LocalDate.of(2020, 1, 1));
        entity.setRegulatoryProfileId(profile);
        entity.setTransferRestricted(restricted);
        entity.setTransferRestrictionReason(reason);
        store.insert(entity);
    }

    private void account(String id, String entityId, String correspondent, String number, String currency, String role) {
        BankAccount account = new BankAccount();
        account.setId(id);
        account.setEntityId(entityId);
        account.setCorrespondentId(correspondent);
        account.setAccountNumber(number);
        account.setCurrency(currency);
        account.setAccountRole(role);
        account.setStatus("ACTIVE");
        account.setAvailable(true);
        account.setValidFrom(LocalDate.of(2020, 1, 1));
        account.setSourceSystem("CORE");
        store.insert(account);
    }

    private void party(String id, String name, String country) {
        Counterparty party = new Counterparty();
        party.setId(id);
        party.setName(name);
        party.setCountry(country);
        party.setStatus("ACTIVE");
        party.setValidFrom(LocalDate.of(2020, 1, 1));
        store.insert(party);
    }

    private void product(String id, String label, boolean fx) {
        TreasuryProduct product = new TreasuryProduct();
        product.setId(id);
        product.setLabel(label);
        product.setStatus("ACTIVE");
        product.setFx(fx);
        store.insert(product);
    }

    private void rate() {
        FxRate rate = new FxRate();
        rate.setId("FX-TND-EUR-20261017");
        rate.setBaseCurrency("TND");
        rate.setQuoteCurrency("EUR");
        rate.setRate(new BigDecimal("0.30000000"));
        rate.setSource("BCT_REFERENCE");
        rate.setRateDate(REPORTING_DATE);
        rate.setMethod("MID");
        rate.setCapturedAt(Instant.parse("2026-10-17T07:00:00Z"));
        store.insert(rate);
    }

    private void position(String id, String entityId, String accountId, String currency, PositionKind kind, String amount, String pair, String source) {
        CashPosition position = new CashPosition();
        position.setId(id);
        position.setEntityId(entityId);
        position.setAccountId(accountId);
        position.setCurrency(currency);
        position.setAsOf(REPORTING_DATE);
        position.setKind(kind);
        position.setAmount(new BigDecimal(amount));
        position.setSourceSystem(source);
        position.setSourceReference(id);
        position.setIntercompanyPairId(pair);
        position.setUpdatedAt(Instant.parse("2026-10-17T07:30:00Z"));
        store.insert(position);
    }

    private void scope() {
        GroupConsolidationScope scope = new GroupConsolidationScope();
        scope.setId("GRP-TN-EU");
        scope.setName("Groupe Tunisie – Europe");
        scope.setReportingCurrency("EUR");
        scope.setStatus("ACTIVE");
        store.insert(scope);
        member("MEM-TN", "TN-BANK");
        member("MEM-EU", "EU-BANK");
        IntercompanyFlow flow = new IntercompanyFlow();
        flow.setId("IC-FLOW-2026-001");
        flow.setPairId("IC-2026-001");
        flow.setPayerEntityId("TN-BANK");
        flow.setReceiverEntityId("EU-BANK");
        flow.setCurrency("EUR");
        flow.setAmount(new BigDecimal("1000000.00"));
        flow.setValueDate(REPORTING_DATE);
        flow.setEliminateAccounting(true);
        flow.setEliminatePrudential(true);
        flow.setAssetPositionId("POS-TN-IC");
        flow.setLiabilityPositionId("POS-EU-IC");
        store.insert(flow);
    }

    private void member(String id, String entityId) {
        ScopeMember member = new ScopeMember();
        member.setId(id);
        member.setScopeId("GRP-TN-EU");
        member.setEntityId(entityId);
        store.insert(member);
    }

    private void ruleTn() {
        RegulatoryRuleVersion rule = rule("RULE-TN-LIQ-BCT-2014-14", "TN-LIQ", Jurisdiction.TN, "BCT-2014-14-BASELINE",
                FormulaCode.TN_LIQUIDITY_STOCK, LocalDate.of(2014, 12, 16),
                "Circulaire BCT n° 2014-14 relative au ratio de liquidité", "REC-TN-001");
        weight(rule.getId(), "CASH_BCT", "1");
        weight(rule.getId(), "SOVEREIGN_TN", "1");
        weight(rule.getId(), "IB_PLACEMENT_LE_1M", "1");
        weight(rule.getId(), "DEMAND_DEPOSITS", "1");
        weight(rule.getId(), "TERM_DEPOSITS_LE_1M", "1");
        minimum(rule.getId(), "1");
        change("TN-LIQ", "BCT-2014-14-BASELINE");
    }

    private void ruleLcr() {
        RegulatoryRuleVersion rule = rule("RULE-EU-LCR-2015-61", "EU-LCR", Jurisdiction.EU, "CRR-LCR-2015-61-BASELINE",
                FormulaCode.EU_LCR, LocalDate.of(2015, 10, 1),
                "Règlement (UE) n° 575/2013 et règlement délégué (UE) 2015/61", "REC-EU-001");
        haircut(rule.getId(), "L1", "0");
        haircut(rule.getId(), "L2A", "0.15");
        haircut(rule.getId(), "L2B", "0.50");
        parameter(rule.getId(), "WEIGHT", "RETAIL_STABLE", "0.05", "RATIO");
        parameter(rule.getId(), "WEIGHT", "WHOLESALE_FINANCIAL", "1", "RATIO");
        parameter(rule.getId(), "WEIGHT", "INFLOW_FINANCIAL", "1", "RATIO");
        parameter(rule.getId(), "CAP_NUM", "L2B", "15", "RATIO");
        parameter(rule.getId(), "CAP_DEN", "L2B", "85", "RATIO");
        parameter(rule.getId(), "CAP_NUM", "L2", "2", "RATIO");
        parameter(rule.getId(), "CAP_DEN", "L2", "3", "RATIO");
        parameter(rule.getId(), "INFLOW_CAP", "*", "0.75", "RATIO");
        minimum(rule.getId(), "1");
        change("EU-LCR", "CRR-LCR-2015-61-BASELINE");
    }

    private void ruleNsfr() {
        RegulatoryRuleVersion rule = rule("RULE-EU-NSFR-CRR", "EU-NSFR", Jurisdiction.EU, "CRR-NSFR-BASELINE",
                FormulaCode.EU_NSFR, LocalDate.of(2021, 6, 28),
                "Règlement (UE) n° 575/2013, financement stable net", "REC-EU-002");
        parameter(rule.getId(), "FACTOR", "CAPITAL_GT_1Y", "1", "RATIO");
        parameter(rule.getId(), "FACTOR", "RETAIL_STABLE", "0.95", "RATIO");
        parameter(rule.getId(), "FACTOR", "HQLA_L1", "0", "RATIO");
        parameter(rule.getId(), "FACTOR", "LOAN_NF_GT_1Y", "0.85", "RATIO");
        parameter(rule.getId(), "FACTOR", "OTHER_ASSETS", "1", "RATIO");
        minimum(rule.getId(), "1");
        change("EU-NSFR", "CRR-NSFR-BASELINE");
    }

    private RegulatoryRuleVersion rule(String id, String ruleId, Jurisdiction jurisdiction, String version, FormulaCode formula,
            LocalDate validFrom, String reference, String test) {
        RegulatoryRuleVersion rule = new RegulatoryRuleVersion();
        rule.setId(id);
        rule.setRuleId(ruleId);
        rule.setJurisdiction(jurisdiction);
        rule.setEntityScope("*");
        rule.setRegulatoryReference(reference);
        rule.setRuleVersion(version);
        rule.setFormulaCode(formula);
        rule.setValidFrom(validFrom);
        rule.setApprovalStatus(ApprovalStatus.PUBLISHED);
        rule.setCreatedBy("system.compliance");
        rule.setApprovedBy("system.validator");
        rule.setTestReference(test);
        rule.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        store.insert(rule);
        return rule;
    }

    private void weight(String ruleId, String component, String value) {
        parameter(ruleId, "WEIGHT", component, value, "RATIO");
    }

    private void haircut(String ruleId, String component, String value) {
        parameter(ruleId, "HAIRCUT", component, value, "RATIO");
    }

    private void minimum(String ruleId, String value) {
        parameter(ruleId, "MINIMUM", "*", value, "RATIO");
    }

    private void parameter(String ruleId, String code, String component, String value, String unit) {
        RegulatoryParameter parameter = new RegulatoryParameter();
        parameter.setId(ruleId + "-" + code + "-" + component.replace('*', 'X'));
        parameter.setRuleVersionId(ruleId);
        parameter.setCode(code);
        parameter.setComponent(component);
        parameter.setValue(new BigDecimal(value));
        parameter.setUnit(unit);
        store.insert(parameter);
    }

    private void change(String ruleId, String version) {
        RegulatoryChangeLog change = new RegulatoryChangeLog();
        change.setId("CHG-" + ruleId);
        change.setRuleId(ruleId);
        change.setToVersion(version);
        change.setChangeSummary("Version de travail publiée, en attente de validation indépendante sur le texte applicable.");
        change.setActor("system.validator");
        change.setChangedAt(Instant.parse("2026-10-01T00:00:00Z"));
        store.insert(change);
    }

    private void item(String entityId, String formula, String side, String bucket, String currency, String amount, String source) {
        LiquidityItem item = new LiquidityItem();
        item.setId(entityId + "-" + formula + "-" + bucket);
        item.setEntityId(entityId);
        item.setAsOf(REPORTING_DATE);
        item.setFormulaCode(formula);
        item.setSide(side);
        item.setBucket(bucket);
        item.setCurrency(currency);
        item.setAmount(new BigDecimal(amount));
        item.setSourceReference(source);
        store.insert(item);
    }

    private void limit() {
        ExposureLimit limit = new ExposureLimit();
        limit.setId("LIM-EU-BNPP");
        limit.setEntityId("EU-BANK");
        limit.setDimension("COUNTERPARTY");
        limit.setDimensionValue("CP-BNPP");
        limit.setCurrency("EUR");
        limit.setLimitAmount(new BigDecimal("10000000.00"));
        limit.setAction(LimitAction.BLOCK);
        limit.setStatus("ACTIVE");
        limit.setValidFrom(LocalDate.of(2026, 1, 1));
        store.insert(limit);
    }

    private void deal() {
        TreasuryDeal deal = new TreasuryDeal();
        deal.setId("DEAL-EU-MM-1");
        deal.setEntityId("EU-BANK");
        deal.setProductCode("IB_BORROW");
        deal.setCurrency("EUR");
        deal.setAmount(new BigDecimal("7000000.00"));
        deal.setCounterpartyId("CP-BNPP");
        deal.setReference("REF-EU-MM-1");
        deal.setTradeDate(LocalDate.of(2026, 10, 1));
        deal.setValueDate(LocalDate.of(2026, 10, 1));
        deal.setMaturityDate(LocalDate.of(2026, 10, 22));
        deal.setStatus(DealStatus.VALIDATED);
        deal.setCreatedBy("system.treasury");
        deal.setApprovedBy("system.validator");
        deal.setCreatedAt(Instant.parse("2026-10-01T09:00:00Z"));
        deal.setApprovedAt(Instant.parse("2026-10-01T10:00:00Z"));
        store.insert(deal);
    }

    private void flow(String id, String entityId, String accountId, String currency, LocalDate date, String amount, FlowClass flowClass, boolean cancellable, String label) {
        ForecastFlow flow = new ForecastFlow();
        flow.setId(id);
        flow.setEntityId(entityId);
        flow.setAccountId(accountId);
        flow.setCurrency(currency);
        flow.setValueDate(date);
        flow.setAmount(new BigDecimal(amount));
        flow.setFlowClass(flowClass);
        flow.setScenarioCode("BASE");
        flow.setLabel(label);
        flow.setCancellable(cancellable);
        flow.setSourceReference(id);
        store.insert(flow);
    }

    private void movement(String id, String reference, String amount) {
        SourceMovement movement = new SourceMovement();
        movement.setId(id);
        movement.setBatchId("SEED");
        movement.setSourceSystem("CORE_BANKING");
        movement.setExternalId(id);
        movement.setEntityId("EU-BANK");
        movement.setAccountId("EU-TARGET-EUR");
        movement.setCurrency("EUR");
        movement.setAmount(new BigDecimal(amount));
        movement.setValueDate(REPORTING_DATE);
        movement.setBookingDate(REPORTING_DATE);
        movement.setReference(reference);
        movement.setStatus("IMPORTED");
        store.insert(movement);
    }
}
