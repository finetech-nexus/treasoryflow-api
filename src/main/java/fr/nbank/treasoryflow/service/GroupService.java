package fr.nbank.treasoryflow.service;

import static fr.nbank.treasoryflow.domain.DecimalPolicy.money;

import fr.nbank.treasoryflow.api.ApiTypes.GroupView;
import fr.nbank.treasoryflow.api.ApiTypes.ValuationLine;
import fr.nbank.treasoryflow.domain.CashPosition;
import fr.nbank.treasoryflow.domain.Codes.PositionKind;
import fr.nbank.treasoryflow.domain.CurrencyValuation;
import fr.nbank.treasoryflow.domain.FxRate;
import fr.nbank.treasoryflow.domain.GroupConsolidationScope;
import fr.nbank.treasoryflow.domain.IntercompanyFlow;
import fr.nbank.treasoryflow.domain.LegalEntity;
import fr.nbank.treasoryflow.domain.ScopeMember;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GroupService {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final TreasuryStore store;
    private final Access access;
    private final AuditService audit;

    public GroupService(TreasuryStore store, Access access, AuditService audit) {
        this.store = store;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<GroupConsolidationScope> scopes() {
        return store.all(GroupConsolidationScope.class);
    }

    @Transactional(readOnly = true)
    public List<IntercompanyFlow> flows(String scopeId) {
        Set<String> members = members(scopeId);
        return store.all(IntercompanyFlow.class).stream()
                .filter(flow -> members.contains(flow.getPayerEntityId()) && members.contains(flow.getReceiverEntityId()))
                .toList();
    }

    @Transactional
    public GroupView consolidate(String scopeId, LocalDate asOf) {
        access.requireAny("TREASURER", "VALIDATOR", "COMPLIANCE", "AUDITOR");
        GroupConsolidationScope scope = store.find(GroupConsolidationScope.class, scopeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Périmètre " + scopeId));
        Set<String> memberIds = members(scopeId);
        memberIds.forEach(access::entity);
        List<CashPosition> positions = store.all(CashPosition.class).stream()
                .filter(position -> memberIds.contains(position.getEntityId()))
                .filter(position -> asOf.equals(position.getAsOf()))
                .filter(position -> position.getKind() == PositionKind.AVAILABLE)
                .toList();
        Map<String, CashPosition> byId = positions.stream().collect(java.util.stream.Collectors.toMap(CashPosition::getId, position -> position));
        List<IntercompanyFlow> flows = store.all(IntercompanyFlow.class).stream()
                .filter(flow -> memberIds.contains(flow.getPayerEntityId()) && memberIds.contains(flow.getReceiverEntityId()))
                .filter(flow -> !flow.getValueDate().isAfter(asOf))
                .filter(IntercompanyFlow::isEliminatePrudential)
                .toList();
        Set<String> eliminatedIds = new HashSet<>();
        List<String> pairs = new ArrayList<>();
        BigDecimal eliminated = BigDecimal.ZERO;
        for (IntercompanyFlow flow : flows) {
            CashPosition asset = byId.get(flow.getAssetPositionId());
            CashPosition liability = byId.get(flow.getLiabilityPositionId());
            if (asset == null || liability == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Jambe intragroupe manquante pour " + flow.getPairId());
            }
            BigDecimal assetReporting = convert(asset.getAmount(), asset.getCurrency(), scope.getReportingCurrency(), asOf).amount();
            BigDecimal liabilityReporting = convert(liability.getAmount(), liability.getCurrency(), scope.getReportingCurrency(), asOf).amount();
            if (assetReporting.add(liabilityReporting).abs().compareTo(TOLERANCE) > 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Élimination déséquilibrée pour " + flow.getPairId());
            }
            eliminated = eliminated.add(assetReporting.abs().max(liabilityReporting.abs()));
            eliminatedIds.add(asset.getId());
            eliminatedIds.add(liability.getId());
            pairs.add(flow.getPairId());
        }
        for (CashPosition position : positions) {
            if (position.getIntercompanyPairId() != null && !eliminatedIds.contains(position.getId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Position intragroupe sans flux d'élimination : " + position.getId());
            }
        }
        String snapshotId = Ids.next();
        BigDecimal consolidated = BigDecimal.ZERO;
        BigDecimal freely = BigDecimal.ZERO;
        BigDecimal restricted = BigDecimal.ZERO;
        List<ValuationLine> lines = new ArrayList<>();
        for (CashPosition position : positions) {
            Quote quote = convert(position.getAmount(), position.getCurrency(), scope.getReportingCurrency(), asOf);
            boolean intercompany = eliminatedIds.contains(position.getId());
            LegalEntity entity = store.find(LegalEntity.class, position.getEntityId()).orElseThrow();
            boolean transferable = !intercompany && !entity.isTransferRestricted();
            String reason = intercompany
                    ? "Flux intragroupe exclu de la liquidité transférable"
                    : entity.isTransferRestricted()
                            ? entity.getTransferRestrictionReason()
                            : "Transférable sous réserve des cut-off et des habilitations";
            if (!intercompany && transferable) {
                freely = freely.add(quote.amount());
            } else if (!intercompany) {
                restricted = restricted.add(quote.amount());
            }
            if (!intercompany) {
                consolidated = consolidated.add(quote.amount());
            }
            lines.add(new ValuationLine(
                    position.getEntityId(), position.getAccountId(), position.getCurrency(), position.getAmount(),
                    quote.rate(), quote.source(), quote.date(), quote.method(), scope.getReportingCurrency(), quote.amount(),
                    intercompany, position.getIntercompanyPairId(), transferable, reason));
            CurrencyValuation valuation = new CurrencyValuation();
            valuation.setId(Ids.next());
            valuation.setContextType("GROUP");
            valuation.setContextId(snapshotId);
            valuation.setOriginalCurrency(position.getCurrency());
            valuation.setOriginalAmount(position.getAmount());
            valuation.setReportingCurrency(scope.getReportingCurrency());
            valuation.setReportingAmount(quote.amount());
            valuation.setRate(quote.rate());
            valuation.setRateSource(quote.source());
            valuation.setRateDate(quote.date());
            valuation.setMethod(quote.method());
            store.insert(valuation);
        }
        audit.record("GROUP_CONSOLIDATION", "GroupConsolidationScope", scopeId, snapshotId);
        return new GroupView(snapshotId, scopeId, asOf, scope.getReportingCurrency(), money(consolidated), money(eliminated),
                money(freely), money(restricted), pairs, lines);
    }

    private Set<String> members(String scopeId) {
        List<ScopeMember> rows = store.query("select m from ScopeMember m where m.scopeId = :scope", ScopeMember.class, Map.of("scope", scopeId));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Périmètre " + scopeId);
        }
        return rows.stream().map(ScopeMember::getEntityId).collect(java.util.stream.Collectors.toSet());
    }

    private Quote convert(BigDecimal amount, String currency, String reporting, LocalDate asOf) {
        if (currency.equals(reporting)) {
            return new Quote(money(amount), BigDecimal.ONE.setScale(8), "IDENTITY", "PARITY", asOf);
        }
        FxRate rate = store.first(
                "select r from FxRate r where r.baseCurrency = :base and r.quoteCurrency = :quote and r.rateDate = :date",
                FxRate.class,
                Map.of("base", currency, "quote", reporting, "date", asOf))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Taux manquant " + currency + "/" + reporting + " au " + asOf));
        return new Quote(money(amount.multiply(rate.getRate())), rate.getRate(), rate.getSource(), rate.getMethod(), rate.getRateDate());
    }

    private record Quote(BigDecimal amount, BigDecimal rate, String source, String method, LocalDate date) {}
}
