package fr.nbank.treasoryflow.service;

import static fr.nbank.treasoryflow.domain.DecimalPolicy.money;

import fr.nbank.treasoryflow.api.ApiTypes.FlowRequest;
import fr.nbank.treasoryflow.api.ApiTypes.GapBucket;
import fr.nbank.treasoryflow.api.ApiTypes.Projection;
import fr.nbank.treasoryflow.api.ApiTypes.ProjectionPoint;
import fr.nbank.treasoryflow.api.ApiTypes.ProjectRequest;
import fr.nbank.treasoryflow.domain.CashPosition;
import fr.nbank.treasoryflow.domain.Codes.DealStatus;
import fr.nbank.treasoryflow.domain.Codes.FlowClass;
import fr.nbank.treasoryflow.domain.Codes.RecommendationStatus;
import fr.nbank.treasoryflow.domain.ForecastFlow;
import fr.nbank.treasoryflow.domain.FundingRecommendation;
import fr.nbank.treasoryflow.domain.LegalEntity;
import fr.nbank.treasoryflow.domain.TreasuryDeal;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ForecastService {
    private static final Set<String> LIABILITY_PRODUCTS = Set.of("IB_BORROW", "REPO");
    private static final EnumSet<DealStatus> LIVE = EnumSet.of(
            DealStatus.VALIDATED, DealStatus.TRANSMITTED, DealStatus.CONFIRMED, DealStatus.SETTLED, DealStatus.RECONCILED);

    private final TreasuryStore store;
    private final Access access;
    private final AuditService audit;
    private final ReferenceService references;
    private final GroupService group;

    public ForecastService(TreasuryStore store, Access access, AuditService audit, ReferenceService references, GroupService group) {
        this.store = store;
        this.access = access;
        this.audit = audit;
        this.references = references;
        this.group = group;
    }

    @Transactional
    public ForecastFlow addFlow(FlowRequest request) {
        access.require("TREASURER");
        access.entity(request.entityId());
        references.requireAccount(request.entityId(), request.accountId(), request.currency());
        ForecastFlow flow = new ForecastFlow();
        flow.setId(Ids.next());
        flow.setEntityId(request.entityId());
        flow.setAccountId(request.accountId());
        flow.setCurrency(request.currency());
        flow.setValueDate(request.valueDate());
        flow.setAmount(money(request.amount()));
        flow.setFlowClass(FlowClass.valueOf(request.flowClass()));
        flow.setScenarioCode(request.scenarioCode());
        flow.setLabel(request.label());
        flow.setCancellable(request.cancellable());
        store.insert(flow);
        audit.record("FORECAST_FLOW", "ForecastFlow", flow.getId(), request.label());
        return flow;
    }

    @Transactional(readOnly = true)
    public Projection project(ProjectRequest request) {
        access.entity(request.entityId());
        int horizon = request.horizonDays() <= 0 ? 30 : Math.min(request.horizonDays(), 366);
        List<CashPosition> opening = references.available(request.entityId(), request.asOf()).stream()
                .filter(position -> position.getIntercompanyPairId() == null)
                .toList();
        List<ForecastFlow> flows = store.all(ForecastFlow.class).stream()
                .filter(flow -> request.entityId().equals(flow.getEntityId()))
                .filter(flow -> "BASE".equals(flow.getScenarioCode()) || request.scenario().equals(flow.getScenarioCode()))
                .toList();
        Map<String, BigDecimal> base = new LinkedHashMap<>();
        for (CashPosition position : opening) {
            base.merge(position.getCurrency(), position.getAmount(), BigDecimal::add);
        }
        String nature = request.scenario().startsWith("STRESS") ? "SIMULATED" : "FORECAST";
        List<ProjectionPoint> points = new ArrayList<>();
        for (int day = 0; day <= horizon; day++) {
            LocalDate date = request.asOf().plusDays(day);
            Map<String, BigDecimal> totals = new LinkedHashMap<>(base);
            for (ForecastFlow flow : flows) {
                LocalDate valueDate = flow.getValueDate();
                if ("STRESS_DELAY".equals(request.scenario()) && flow.getAmount().signum() > 0) {
                    valueDate = valueDate.plusDays(5);
                }
                if ("STRESS_FUNDING".equals(request.scenario()) && flow.isCancellable()) {
                    continue;
                }
                if (!valueDate.isAfter(date)) {
                    totals.merge(flow.getCurrency(), flow.getAmount(), BigDecimal::add);
                }
            }
            for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
                points.add(new ProjectionPoint(date, entry.getKey(), money(entry.getValue()), nature));
            }
        }
        return new Projection(request.entityId(), request.scenario(), request.asOf(), nature, points);
    }

    @Transactional(readOnly = true)
    public List<GapBucket> gaps(String entityId, LocalDate asOf) {
        access.entity(entityId);
        Map<String, GapBucket> buckets = new LinkedHashMap<>();
        for (TreasuryDeal deal : store.all(TreasuryDeal.class)) {
            if (!entityId.equals(deal.getEntityId()) || !LIVE.contains(deal.getStatus()) || deal.getMaturityDate() == null) {
                continue;
            }
            String name = bucket(asOf, deal.getMaturityDate());
            String key = name + "|" + deal.getCurrency();
            GapBucket current = buckets.getOrDefault(key, new GapBucket(name, deal.getCurrency(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            boolean liability = LIABILITY_PRODUCTS.contains(deal.getProductCode());
            BigDecimal asset = current.asset().add(liability ? BigDecimal.ZERO : deal.getAmount());
            BigDecimal debt = current.liability().add(liability ? deal.getAmount() : BigDecimal.ZERO);
            buckets.put(key, new GapBucket(name, deal.getCurrency(), money(asset), money(debt), money(asset.subtract(debt))));
        }
        return List.copyOf(buckets.values());
    }

    @Transactional
    public List<FundingRecommendation> analyse(String scopeId, LocalDate asOf) {
        access.require("TREASURER");
        var consolidation = group.consolidate(scopeId, asOf);
        List<FundingRecommendation> created = new ArrayList<>();
        for (String entityId : store.query("select m.entityId from ScopeMember m where m.scopeId = :scope", String.class, Map.of("scope", scopeId))) {
            Projection projection = project(new ProjectRequest(entityId, "BASE", asOf, 30));
            Map<String, BigDecimal> worst = new LinkedHashMap<>();
            Map<String, LocalDate> worstDate = new LinkedHashMap<>();
            for (ProjectionPoint point : projection.points()) {
                BigDecimal current = worst.get(point.currency());
                if (current == null || point.projected().compareTo(current) < 0) {
                    worst.put(point.currency(), point.projected());
                    worstDate.put(point.currency(), point.date());
                }
            }
            for (Map.Entry<String, BigDecimal> entry : worst.entrySet()) {
                if (entry.getValue().signum() >= 0) {
                    continue;
                }
                BigDecimal deficit = entry.getValue().abs();
                List<String> blocked = consolidation.lines().stream()
                        .filter(line -> !line.entityId().equals(entityId))
                        .filter(line -> !line.freelyTransferable())
                        .map(line -> line.entityId())
                        .distinct()
                        .toList();
                String proposed = consolidation.lines().stream()
                        .filter(line -> !line.entityId().equals(entityId))
                        .filter(line -> line.freelyTransferable())
                        .filter(line -> entry.getKey().equals(line.originalCurrency()))
                        .map(line -> line.entityId())
                        .findFirst()
                        .orElse(null);
                if (proposed != null) {
                    LegalEntity source = references.entity(proposed);
                    if (source.isTransferRestricted()) {
                        throw new IllegalStateException("Une liquidité restreinte a été proposée comme transférable");
                    }
                }
                String explanation = "Déficit " + entry.getKey() + " de " + money(deficit) + " au " + worstDate.get(entry.getKey())
                        + " sur " + entityId + ". "
                        + (blocked.isEmpty() ? "" : "Sources non transférables : " + String.join(", ", blocked)
                                + ". Une position disponible dans une entité soumise à restriction n'est pas proposée comme librement transférable. ")
                        + (proposed == null
                                ? "Aucun transfert intragroupe de même devise n'est disponible. Action proposée : financement local."
                                : "Source proposée : " + proposed + ", sous réserve de validation.");
                store.all(FundingRecommendation.class).stream()
                        .filter(existing -> entityId.equals(existing.getEntityId()))
                        .filter(existing -> entry.getKey().equals(existing.getCurrency()))
                        .filter(existing -> asOf.equals(existing.getAsOf()))
                        .filter(existing -> existing.getStatus() == RecommendationStatus.DRAFT)
                        .forEach(existing -> existing.setStatus(RecommendationStatus.REJECTED));
                FundingRecommendation recommendation = new FundingRecommendation();
                recommendation.setId(Ids.next());
                recommendation.setEntityId(entityId);
                recommendation.setCurrency(entry.getKey());
                recommendation.setAsOf(asOf);
                recommendation.setDeficit(money(deficit));
                recommendation.setSuggestedProduct(proposed == null ? "IB_BORROW" : "INTRAGROUP");
                recommendation.setSuggestedAmount(money(deficit));
                recommendation.setProposedSourceEntityId(proposed);
                recommendation.setBlockedSources(String.join(",", blocked));
                recommendation.setProposesRestrictedTransfer(false);
                recommendation.setExplanation(explanation);
                recommendation.setStatus(RecommendationStatus.DRAFT);
                recommendation.setCreatedBy(access.current().id());
                recommendation.setCreatedAt(Instant.now());
                store.insert(recommendation);
                created.add(recommendation);
                audit.record("FUNDING_RECOMMENDATION", "FundingRecommendation", recommendation.getId(), explanation);
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<FundingRecommendation> recommendations(String entityId) {
        return store.all(FundingRecommendation.class).stream()
                .filter(recommendation -> access.current().canSee(recommendation.getEntityId()))
                .filter(recommendation -> entityId == null || entityId.isBlank() || entityId.equals(recommendation.getEntityId()))
                .toList();
    }

    @Transactional
    public FundingRecommendation approve(String id) {
        access.require("VALIDATOR");
        FundingRecommendation recommendation = store.find(FundingRecommendation.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommandation " + id));
        access.entity(recommendation.getEntityId());
        access.fourEyes(recommendation.getCreatedBy());
        if (recommendation.isProposesRestrictedTransfer()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfert restreint non approuvable");
        }
        if (recommendation.getStatus() != RecommendationStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La recommandation n'est plus en brouillon");
        }
        recommendation.setStatus(RecommendationStatus.APPROVED);
        recommendation.setApprovedBy(access.current().id());
        audit.record("FUNDING_APPROVE", "FundingRecommendation", id, recommendation.getApprovedBy());
        return recommendation;
    }

    private static String bucket(LocalDate asOf, LocalDate maturity) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(asOf, maturity);
        if (days <= 7) {
            return "ON_1W";
        }
        if (days <= 30) {
            return "M1";
        }
        if (days <= 90) {
            return "M3";
        }
        if (days <= 365) {
            return "Y1";
        }
        return "GT_1Y";
    }
}
