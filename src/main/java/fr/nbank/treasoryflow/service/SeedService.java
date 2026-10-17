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
import fr.nbank.treasoryflow.domain.ConnectivityChannel;
import fr.nbank.treasoryflow.domain.Counterparty;
import fr.nbank.treasoryflow.domain.ExposureLimit;
import fr.nbank.treasoryflow.domain.ForecastFlow;
import fr.nbank.treasoryflow.domain.FxRate;
import fr.nbank.treasoryflow.domain.GroupConsolidationScope;
import fr.nbank.treasoryflow.domain.IntercompanyFlow;
import fr.nbank.treasoryflow.domain.LegalEntity;
import fr.nbank.treasoryflow.domain.LiquidityItem;
import fr.nbank.treasoryflow.domain.PaymentOrder;
import fr.nbank.treasoryflow.domain.PaymentProfile;
import fr.nbank.treasoryflow.domain.PlatformParameter;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeedService implements CommandLineRunner {
    static final String NEXUS = "Nexus Bank";

    private static final String DISCLAIMER =
            "Profil de liquidité du périmètre tunisien de Nexus Bank. Les pondérations restent à valider sur les textes consolidés. Ce profil n'est pas une certification réglementaire.";

    private final TreasuryStore store;
    private final boolean workbook;

    public SeedService(TreasuryStore store, @Value("${treasoryflow.seed.workbook:false}") boolean workbook) {
        this.store = store;
        this.workbook = workbook;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (store.find(LegalEntity.class, "TN-BANK").isPresent()) {
            refreshNexusIdentity();
            clearPublishedBooks();
            tunisianBanks();
            cashServices();
            platform();
            if (!workbook) {
                clearWorkbook();
            }
            store.flush();
            return;
        }
        jurisdiction("TN", "Banque centrale de Tunisie", "Profil de liquidité et de déclaration propre aux entités tunisiennes.");
        jurisdiction("EU", "BCE et autorité nationale compétente", "LCR, NSFR et reporting prudentiel européen, sans application automatique hors UE.");
        profile("PROF-TN", "TN-BANK", Jurisdiction.TN, "Profil BCT — Nexus Bank");
        profile("PROF-EU", "EU-BANK", Jurisdiction.EU, "Profil CRR");
        entity("TN-BANK", NEXUS, "TN", Jurisdiction.TN, "TND", "PROF-TN", true,
                "Réglementation des changes et contrainte de liquidité locale : la trésorerie tunisienne n'est pas librement transférable vers une entité européenne.");
        entity("EU-BANK", "Nexus Bank — Europe", "FR", Jurisdiction.EU, "EUR", "PROF-EU", false, null);
        applyNexusIdentity(store.find(LegalEntity.class, "TN-BANK").orElseThrow());
        account("TN-BCT-TND", "TN-BANK", "CP-BCT", "BCT-TND-001", "TND", "BCT");
        account("TN-NOSTRO-EUR", "TN-BANK", "CP-EU-BANK", "NOSTRO-EUR-001", "EUR", "NOSTRO");
        account("EU-TARGET-EUR", "EU-BANK", "CP-BNPP", "TARGET-EUR-001", "EUR", "TARGET");
        party("CP-BCT", "Banque centrale de Tunisie", "TN");
        party("CP-BNPP", "BNP Paribas", "FR");
        party("CP-EU-BANK", "Nexus Bank — Europe", "FR");
        party("CP-TN-BANK", NEXUS, "TN");
        tunisianBanks();
        product("FX_SPOT", "Change au comptant", true);
        product("FX_FORWARD", "Change à terme", true);
        product("FX_SWAP", "Swap de change", true);
        product("IB_PLACE", "Placement interbancaire", false);
        product("IB_BORROW", "Emprunt interbancaire", false);
        product("REPO", "Pension livrée", false);
        product("REVERSE_REPO", "Pension livrée inverse", false);
        rate();
        scope();
        ruleTn();
        ruleLcr();
        ruleNsfr();
        limit();
        if (workbook) {
            workbook();
        }
        cashServices();
        platform();
        store.flush();
    }

    private void workbook() {
        position("POS-TN-BCT-BOOK", "TN-BANK", "TN-BCT-TND", "TND", PositionKind.BOOKED, "500000000.00", null, "CORE");
        position("POS-TN-BCT-AVL", "TN-BANK", "TN-BCT-TND", "TND", PositionKind.AVAILABLE, "500000000.00", null, "CORE");
        position("POS-TN-IC", "TN-BANK", "TN-NOSTRO-EUR", "EUR", PositionKind.AVAILABLE, "1000000.00", "IC-2026-001", "TMS");
        position("POS-EU-CASH-BOOK", "EU-BANK", "EU-TARGET-EUR", "EUR", PositionKind.BOOKED, "5000000.00", null, "CORE");
        position("POS-EU-CASH-AVL", "EU-BANK", "EU-TARGET-EUR", "EUR", PositionKind.AVAILABLE, "5000000.00", null, "CORE");
        position("POS-EU-IC", "EU-BANK", "EU-TARGET-EUR", "EUR", PositionKind.AVAILABLE, "-1000000.00", "IC-2026-001", "TMS");
        intercompany();
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
        deal();
        flow("FLOW-EU-FUND", "EU-BANK", "EU-TARGET-EUR", "EUR", LocalDate.of(2026, 10, 20), "3000000.00", FlowClass.ESTIMATED, true, "Tirage de financement annulable");
        flow("FLOW-EU-OUT", "EU-BANK", "EU-TARGET-EUR", "EUR", LocalDate.of(2026, 10, 22), "-10000000.00", FlowClass.CONTRACTUAL, false, "Échéance de financement");
        movement("MV-MATCH", "REF-EU-MM-1", "7000000.00");
        movement("MV-ORPHAN", "ORPHAN-1", "100.00");
        AuditEvent event = new AuditEvent();
        event.setId("AUD-SEED");
        event.setOccurredAt(Instant.parse("2026-10-17T08:00:00Z"));
        event.setActor("system.compliance");
        event.setAction("SEED");
        event.setSubjectType("LegalEntity");
        event.setSubjectId("GRP-TN-EU");
        event.setDetail("Référentiel Nexus Bank et place bancaire tunisienne");
        store.insert(event);
    }

    private void clearWorkbook() {
        drop(IntercompanyFlow.class, "IC-FLOW-2026-001");
        drop(PaymentOrder.class, "PAY-EU-SUP-1");
        drop(TreasuryDeal.class, "DEAL-EU-MM-1");
        drop(ForecastFlow.class, "FLOW-EU-FUND", "FLOW-EU-OUT");
        drop(SourceMovement.class, "MV-MATCH", "MV-ORPHAN");
        drop(CashPosition.class, "POS-TN-BCT-BOOK", "POS-TN-BCT-AVL", "POS-TN-IC", "POS-EU-CASH-BOOK", "POS-EU-CASH-AVL", "POS-EU-IC");
        drop(LiquidityItem.class,
                "TN-BANK-TN_LIQUIDITY_STOCK-CASH_BCT",
                "TN-BANK-TN_LIQUIDITY_STOCK-SOVEREIGN_TN",
                "TN-BANK-TN_LIQUIDITY_STOCK-IB_PLACEMENT_LE_1M",
                "TN-BANK-TN_LIQUIDITY_STOCK-DEMAND_DEPOSITS",
                "TN-BANK-TN_LIQUIDITY_STOCK-TERM_DEPOSITS_LE_1M",
                "EU-BANK-EU_LCR-L1",
                "EU-BANK-EU_LCR-L2A",
                "EU-BANK-EU_LCR-L2B",
                "EU-BANK-EU_LCR-RETAIL_STABLE",
                "EU-BANK-EU_LCR-WHOLESALE_FINANCIAL",
                "EU-BANK-EU_LCR-INFLOW_FINANCIAL",
                "EU-BANK-EU_NSFR-CAPITAL_GT_1Y",
                "EU-BANK-EU_NSFR-RETAIL_STABLE",
                "EU-BANK-EU_NSFR-HQLA_L1",
                "EU-BANK-EU_NSFR-LOAN_NF_GT_1Y",
                "EU-BANK-EU_NSFR-OTHER_ASSETS");
        drop(BucketClassification.class, "CLS-TN-CASH");
        drop(AuditEvent.class, "AUD-SEED");
    }

    private void drop(Class<?> type, String... ids) {
        for (String id : ids) {
            store.find(type, id).ifPresent(store::remove);
        }
    }

    private void refreshNexusIdentity() {
        store.find(LegalEntity.class, "TN-BANK").ifPresent(this::applyNexusIdentity);
        store.find(LegalEntity.class, "EU-BANK").ifPresent(entity -> entity.setLegalName("Nexus Bank — Europe"));
        store.find(Counterparty.class, "CP-TN-BANK").ifPresent(party -> party.setName(NEXUS));
        store.find(Counterparty.class, "CP-EU-BANK").ifPresent(party -> party.setName("Nexus Bank — Europe"));
        store.find(RegulatoryProfile.class, "PROF-TN").ifPresent(profile -> profile.setName("Profil BCT — Nexus Bank"));
        store.find(CashPool.class, "POOL-TND").ifPresent(pool -> pool.setName("Pool TND notionnel"));
    }

    private void applyNexusIdentity(LegalEntity entity) {
        entity.setLegalName(NEXUS);
        entity.setSwiftBic(null);
        entity.setHeadquarters("Périmètre de trésorerie, Tunisie");
        entity.setRegistration(null);
        entity.setWebsite(null);
    }

    private void clearPublishedBooks() {
        for (String id : java.util.List.of("PUB-BCT-CASH", "PUB-BCT-BALANCES", "PUB-BANKS", "PUB-CUSTOMERS")) {
            store.find(PublishedIndicator.class, id).ifPresent(store::remove);
        }
    }

    private void tunisianBanks() {
        bank("CP-BK-STB", "Société Tunisienne de Banque", "STBKTNTT", "https://www.stb.com.tn");
        bank("CP-BK-BNA", "Banque Nationale Agricole", "BNTETNTT", "https://www.bna.com.tn");
        bank("CP-BK-BH", "Banque de l'Habitat", "BHBKTNTT", "https://www.bh.com.tn");
        bank("CP-BK-BFPME", "Banque de Financement des Petites et Moyennes Entreprises", null, "https://www.bfpme.com.tn");
        bank("CP-BK-BTS", "Banque Tunisienne de Solidarité", null, "https://www.bts.com.tn");
        bank("CP-BK-BTE", "Banque de Tunisie et des Emirats", "BTEXTNTT", "https://www.bte.com.tn");
        bank("CP-BK-BTL", "Banque Tuniso-Libyenne", "ATLDTNTT", "https://www.btl.com.tn");
        bank("CP-BK-TSB", "Tunisian Saudi Bank", "TSIDTNTT", "https://www.stusidbank.com.tn");
        bank("CP-BK-ZITOUNA", "Banque Zitouna", "BZITTNTT", "https://www.banquezitouna.com");
        bank("CP-BK-BARAKA", "Al Baraka Bank", null, "https://www.albarakabank.com.tn");
        bank("CP-BK-WIFAK", "Al Wifak International Bank", "WKIBTNTT", "https://www.wifakbank.com");
        bank("CP-BK-AMEN", "Amen Bank", "CFCTTNTT", "https://www.amenbank.com.tn");
        bank("CP-BK-ATTIJARI", "Attijari Bank", "BSTUTNTT", "https://www.attijaribank.com.tn");
        bank("CP-BK-ATB", "Arab Tunisian Bank", "ATBKTNTT", "https://www.atb.com.tn");
        bank("CP-BK-ABC", "Bank ABC Tunisie", null, "https://www.bank-abc.com");
        bank("CP-BK-BIAT", "Banque Internationale Arabe de Tunisie", "BIATTNTT", "https://www.biat.com.tn");
        bank("CP-BK-BT", "Banque de Tunisie", "BTBKTNTT", "https://www.bt.com.tn");
        bank("CP-BK-BTK", "Banque Tuniso Koweitienne", "BTKOTNTI", "https://www.btknet.com");
        bank("CP-BK-BFT", "Banque Franco Tunisienne", null, null);
        bank("CP-BK-CITI", "Citi Bank", null, null);
        bank("CP-BK-QNB", "Qatar National Bank-Tunis", "BTQITNTI", "https://www.qnb.com.tn");
        bank("CP-BK-UBCI", "Union Bancaire de Commerce et d'Industrie", null, "https://www.ubcinet.net");
        bank("CP-BK-UIB", "Union Internationale de Banque", null, "https://www.uib.com.tn");
    }

    private void bank(String id, String name, String swift, String website) {
        Counterparty party = store.find(Counterparty.class, id).orElse(null);
        if (party == null) {
            party = new Counterparty();
            party.setId(id);
            party.setCountry("TN");
            party.setStatus("ACTIVE");
            party.setValidFrom(LocalDate.of(2020, 1, 1));
            party.setName(name);
            party.setSwiftBic(swift);
            party.setWebsite(website);
            store.insert(party);
            return;
        }
        party.setName(name);
        party.setSwiftBic(swift);
        party.setWebsite(website);
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
            pool.setName("Pool TND notionnel");
            pool.setCurrency("TND");
            pool.setHeaderEntityId("TN-BANK");
            pool.setHeaderAccountId("TN-BCT-TND");
            pool.setType(PoolType.NOTIONAL);
            pool.setStatus("ACTIVE");
            store.insert(pool);
            poolMember("POOL-TND-BCT", "POOL-TND", "TN-BANK", "TN-BCT-TND");
        }
        if (workbook && store.find(PaymentOrder.class, "PAY-EU-SUP-1").isEmpty()) {
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

    private void platform() {
        parameter("SGMT_FLOOR_TND", "100000.00", "TND", "Seuil clientèle du SGMT, système RTGS de la BCT");
        parameter("TARGET_FLOOR_EUR", "1000000.00", "EUR", "Seuil à partir duquel un paiement européen part en TARGET");
        parameter("SIBTEL_LAG_DAYS", "1.00", "DAY", "Dénouement de la télécompensation SIBTEL");
        parameter("SGMT_CUTOFF", "16:30", "Africa/Tunis", "Heure limite de remise SGMT");
        parameter("SIBTEL_CUTOFF", "12:00", "Africa/Tunis", "Heure limite de remise télécompensation");
        String notice = "Adaptateur du bac à sable. Le message est produit ici et n'est pas émis vers le réseau.";
        channel("CH-SGMT", "SGMT", "Règlement brut BCT", "TN", "TND", "ISO 20022", "pacs.008", "OUT", "ACTIVE",
                "100000.00", null, "16:30", 0, notice);
        channel("CH-SIBTEL", "SIBTEL", "Télécompensation", "TN", "TND", "Fichier", "VIR-COMP", "OUT", "ACTIVE",
                null, "99999.99", "12:00", 1, notice);
        channel("CH-SWIFT", "SWIFT", "Correspondant international", "*", "*", "MT", "MT103", "BOTH", "ACTIVE",
                null, null, "17:00", 2, notice);
        channel("CH-SEPA", "SEPA", "Virement européen", "EU", "EUR", "ISO 20022", "pain.001", "OUT", "ACTIVE",
                null, null, "15:30", 1, notice);
        channel("CH-TARGET", "TARGET", "Gros montant européen", "EU", "EUR", "ISO 20022", "pacs.008", "OUT", "ACTIVE",
                "1000000.00", null, "17:00", 0, notice);
        channel("CH-CORE", "CORE_BANKING", "Core bancaire", "*", "*", "API", "NEXUS-CORE", "IN", "ACTIVE",
                null, null, null, 0, "Lots de soldes et de mouvements, rejouables sans doublon.");
        channel("CH-MT940", "MT940", "Relevé de fin de journée", "*", "*", "MT", "MT940", "IN", "ACTIVE",
                null, null, "20:00", 0, notice);
        channel("CH-CAMT", "CAMT053", "Relevé ISO 20022", "*", "*", "ISO 20022", "camt.053", "IN", "READY",
                null, null, "20:00", 0, "Réservé. L'import actif du bac à sable est le MT940.");
        channel("CH-GL", "ACCOUNTING", "Journal comptable", "*", "*", "API", "JOURNAL", "OUT", "READY",
                null, null, null, 0, "Les écritures définitives restent dans le système comptable officiel.");
        profile("PRF-TN-SALARY", "SALARY", "TN", "TND", "FIXED", "SIBTEL");
        profile("PRF-TN-TREASURY", "TREASURY_TRANSFER", "TN", "TND", "FIXED", "SGMT");
        profile("PRF-TN-SUPPLIER", "SUPPLIER", "TN", "TND", "BY_AMOUNT", null);
        profile("PRF-TN-TAX", "TAX", "TN", "TND", "BY_AMOUNT", null);
        profile("PRF-EU-SUPPLIER", "SUPPLIER", "EU", "EUR", "BY_AMOUNT", null);
        profile("PRF-TN-SWIFT", "SUPPLIER", "TN", "EUR", "FIXED", "SWIFT");
    }

    private void parameter(String code, String value, String unit, String label) {
        if (store.find(PlatformParameter.class, code).isPresent()) {
            return;
        }
        PlatformParameter parameter = new PlatformParameter();
        parameter.setCode(code);
        parameter.setValue(value);
        parameter.setUnit(unit);
        parameter.setLabel(label);
        store.insert(parameter);
    }

    private void channel(String id, String code, String name, String jurisdiction, String currency, String protocol,
            String format, String direction, String status, String min, String max, String cutoff, int lag, String notice) {
        if (store.find(ConnectivityChannel.class, id).isPresent()) {
            return;
        }
        ConnectivityChannel channel = new ConnectivityChannel();
        channel.setId(id);
        channel.setCode(code);
        channel.setName(name);
        channel.setJurisdiction(jurisdiction);
        channel.setCurrency(currency);
        channel.setProtocol(protocol);
        channel.setMessageFormat(format);
        channel.setDirection(direction);
        channel.setStatus(status);
        channel.setMinAmount(min == null ? null : new BigDecimal(min));
        channel.setMaxAmount(max == null ? null : new BigDecimal(max));
        channel.setCutoff(cutoff);
        channel.setSettlementLagDays(lag);
        channel.setNotice(notice);
        store.insert(channel);
    }

    private void profile(String id, String purpose, String jurisdiction, String currency, String rule, String channelCode) {
        if (store.find(PaymentProfile.class, id).isPresent()) {
            return;
        }
        PaymentProfile profile = new PaymentProfile();
        profile.setId(id);
        profile.setPurpose(purpose);
        profile.setJurisdiction(jurisdiction);
        profile.setCurrency(currency);
        profile.setRule(rule);
        profile.setChannelCode(channelCode);
        store.insert(profile);
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
    }

    private void intercompany() {
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
