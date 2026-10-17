package fr.nbank.treasoryflow.service;

import static fr.nbank.treasoryflow.domain.DecimalPolicy.money;
import static fr.nbank.treasoryflow.domain.DecimalPolicy.sameMoney;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.nbank.treasoryflow.api.ApiTypes.CalculationRequest;
import fr.nbank.treasoryflow.api.ApiTypes.ItemRequest;
import fr.nbank.treasoryflow.api.ApiTypes.ParameterLine;
import fr.nbank.treasoryflow.api.ApiTypes.ReplayView;
import fr.nbank.treasoryflow.api.ApiTypes.RuleDraft;
import fr.nbank.treasoryflow.domain.BucketClassification;
import fr.nbank.treasoryflow.domain.CashPosition;
import fr.nbank.treasoryflow.domain.Codes.ApprovalStatus;
import fr.nbank.treasoryflow.domain.Codes.FormulaCode;
import fr.nbank.treasoryflow.domain.Codes.Jurisdiction;
import fr.nbank.treasoryflow.domain.Codes.PositionKind;
import fr.nbank.treasoryflow.domain.ComplianceApproval;
import fr.nbank.treasoryflow.domain.LegalEntity;
import fr.nbank.treasoryflow.domain.LiquidityItem;
import fr.nbank.treasoryflow.domain.RegulatoryCalculationRun;
import fr.nbank.treasoryflow.domain.RegulatoryChangeLog;
import fr.nbank.treasoryflow.domain.RegulatoryJurisdiction;
import fr.nbank.treasoryflow.domain.RegulatoryParameter;
import fr.nbank.treasoryflow.domain.RegulatoryProfile;
import fr.nbank.treasoryflow.domain.RegulatoryReport;
import fr.nbank.treasoryflow.domain.RegulatoryResultDetail;
import fr.nbank.treasoryflow.domain.RegulatoryRuleVersion;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.regulatory.CalculationSnapshot;
import fr.nbank.treasoryflow.regulatory.Item;
import fr.nbank.treasoryflow.regulatory.ItemSnap;
import fr.nbank.treasoryflow.regulatory.LiquidityCalculator;
import fr.nbank.treasoryflow.regulatory.Outcome;
import fr.nbank.treasoryflow.regulatory.ParameterSnap;
import fr.nbank.treasoryflow.security.Access;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegulatoryService {
    private final TreasuryStore store;
    private final Access access;
    private final AuditService audit;
    private final ReferenceService references;
    private final ObjectMapper json;

    public RegulatoryService(TreasuryStore store, Access access, AuditService audit, ReferenceService references, ObjectMapper json) {
        this.store = store;
        this.access = access;
        this.audit = audit;
        this.references = references;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<RegulatoryJurisdiction> jurisdictions() {
        return store.all(RegulatoryJurisdiction.class);
    }

    @Transactional(readOnly = true)
    public List<RegulatoryProfile> profiles() {
        return store.all(RegulatoryProfile.class).stream()
                .filter(profile -> access.current().canSee(profile.getEntityId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegulatoryRuleVersion> rules() {
        return store.all(RegulatoryRuleVersion.class);
    }

    @Transactional(readOnly = true)
    public List<RegulatoryParameter> parameters(String ruleVersionId) {
        return store.query("select p from RegulatoryParameter p where p.ruleVersionId = :id",
                RegulatoryParameter.class, Map.of("id", ruleVersionId));
    }

    @Transactional
    public RegulatoryRuleVersion create(RuleDraft draft) {
        access.require("COMPLIANCE");
        FormulaCode formula = formula(draft.formulaCode());
        Jurisdiction jurisdiction = jurisdiction(draft.jurisdiction());
        if (formula.jurisdiction != jurisdiction) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La formule " + formula + " ne peut pas être publiée sous la juridiction " + jurisdiction);
        }
        RegulatoryRuleVersion rule = new RegulatoryRuleVersion();
        rule.setId(Ids.next());
        rule.setRuleId(draft.ruleId());
        rule.setJurisdiction(jurisdiction);
        rule.setEntityScope(draft.entityScope());
        rule.setRegulatoryReference(draft.regulatoryReference());
        rule.setRuleVersion(draft.ruleVersion());
        rule.setFormulaCode(formula);
        rule.setValidFrom(draft.validFrom());
        rule.setApprovalStatus(ApprovalStatus.DRAFT);
        rule.setCreatedBy(access.current().id());
        rule.setTestReference(draft.testReference());
        rule.setCreatedAt(Instant.now());
        store.insert(rule);
        for (ParameterLine line : draft.parameters()) {
            RegulatoryParameter parameter = new RegulatoryParameter();
            parameter.setId(Ids.next());
            parameter.setRuleVersionId(rule.getId());
            parameter.setCode(line.code());
            parameter.setComponent(line.component());
            parameter.setValue(line.value());
            parameter.setUnit(line.unit());
            store.insert(parameter);
        }
        audit.record("RULE_DRAFT", "RegulatoryRuleVersion", rule.getId(), rule.getRuleVersion());
        return rule;
    }

    @Transactional
    public RegulatoryRuleVersion publish(String id) {
        access.require("COMPLIANCE");
        RegulatoryRuleVersion rule = store.find(RegulatoryRuleVersion.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle " + id));
        access.fourEyes(rule.getCreatedBy());
        if (rule.getApprovalStatus() != ApprovalStatus.DRAFT && rule.getApprovalStatus() != ApprovalStatus.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une version brouillon ou validée peut être publiée");
        }
        if (rule.getFormulaCode().jurisdiction != rule.getJurisdiction()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Juridiction et formule incohérentes");
        }
        List<RegulatoryRuleVersion> previous = store.all(RegulatoryRuleVersion.class).stream()
                .filter(other -> other.getRuleId().equals(rule.getRuleId()))
                .filter(other -> other.getJurisdiction() == rule.getJurisdiction())
                .filter(other -> other.getApprovalStatus() == ApprovalStatus.PUBLISHED)
                .filter(other -> !other.getId().equals(rule.getId()))
                .toList();
        for (RegulatoryRuleVersion old : previous) {
            if (old.getValidTo() == null || !old.getValidTo().isBefore(rule.getValidFrom())) {
                LocalDate end = rule.getValidFrom().minusDays(1);
                if (end.isBefore(old.getValidFrom())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Chevauchement de dates d'effet");
                }
                old.setValidTo(end);
            }
        }
        String fromVersion = previous.stream().map(RegulatoryRuleVersion::getRuleVersion).findFirst().orElse(null);
        rule.setApprovalStatus(ApprovalStatus.PUBLISHED);
        rule.setApprovedBy(access.current().id());
        RegulatoryChangeLog change = new RegulatoryChangeLog();
        change.setId(Ids.next());
        change.setRuleId(rule.getRuleId());
        change.setFromVersion(fromVersion);
        change.setToVersion(rule.getRuleVersion());
        change.setChangeSummary("Publication de " + rule.getRuleVersion() + " sans réécriture des calculs arrêtés");
        change.setActor(access.current().id());
        change.setChangedAt(Instant.now());
        store.insert(change);
        approve("RegulatoryRuleVersion", rule.getId(), "PUBLISHED");
        audit.record("RULE_PUBLISH", "RegulatoryRuleVersion", rule.getId(), rule.getRuleVersion());
        return rule;
    }

    @Transactional
    public LiquidityItem addItem(ItemRequest request) {
        access.require("COMPLIANCE");
        access.entity(request.entityId());
        references.entity(request.entityId());
        formula(request.formulaCode());
        LiquidityItem item = new LiquidityItem();
        item.setId(Ids.next());
        item.setEntityId(request.entityId());
        item.setAsOf(request.asOf());
        item.setFormulaCode(request.formulaCode());
        item.setSide(request.side());
        item.setBucket(request.bucket());
        item.setCurrency(request.currency());
        item.setAmount(money(request.amount()));
        item.setSourceReference(request.sourceReference());
        store.insert(item);
        audit.record("LIQUIDITY_ITEM", "LiquidityItem", item.getId(), request.sourceReference());
        return item;
    }

    @Transactional
    public RunView run(CalculationRequest request) {
        access.requireAny("TREASURER", "COMPLIANCE");
        access.entity(request.entityId());
        LegalEntity entity = references.entity(request.entityId());
        FormulaCode formula = formula(request.formulaCode());
        if (formula.jurisdiction != entity.getJurisdiction()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La formule " + formula + " (" + formula.jurisdiction + ") ne s'applique pas à l'entité "
                            + entity.getId() + " (" + entity.getJurisdiction() + ")");
        }
        RegulatoryRuleVersion rule = resolve(formula, request.entityId(), request.referenceDate());
        List<RegulatoryParameter> parameters = parameters(rule.getId());
        List<LiquidityItem> rows = items(request.entityId(), formula, request.referenceDate());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Aucune donnée source à la date demandée");
        }
        Map<String, BigDecimal> values = parameterMap(parameters);
        BigDecimal threshold = values.get("MINIMUM|*");
        if (threshold == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Paramètre MINIMUM manquant");
        }
        Outcome outcome = LiquidityCalculator.calculate(formula, values, toItems(rows));
        List<RegulatoryResultDetail> details = new ArrayList<>();
        outcome.lines().forEach(line -> details.add(detail(null, line.component(), line.side(), line.amount(), line.weight(), line.contribution(), line.explanation())));
        coherence(entity.getId(), formula, request.referenceDate(), rows, details);
        CalculationSnapshot snapshot = new CalculationSnapshot(
                formula.name(),
                rule.getId(),
                rule.getRuleVersion(),
                parameters.stream().map(parameter -> new ParameterSnap(parameter.getCode(), parameter.getComponent(), parameter.getValue())).toList(),
                rows.stream().map(row -> new ItemSnap(row.getSide(), row.getBucket(), row.getAmount(), row.getSourceReference())).toList());
        String body = write(snapshot);
        RegulatoryCalculationRun run = new RegulatoryCalculationRun();
        run.setId(Ids.next());
        run.setRuleVersionId(rule.getId());
        run.setRuleId(rule.getRuleId());
        run.setRuleVersion(rule.getRuleVersion());
        run.setFormulaCode(formula.name());
        run.setJurisdiction(entity.getJurisdiction().name());
        run.setEntityId(entity.getId());
        run.setReferenceDate(request.referenceDate());
        run.setResult(outcome.result());
        run.setUnit("RATIO");
        run.setThreshold(threshold.setScale(6, RoundingMode.HALF_UP));
        run.setThresholdMet(outcome.result().compareTo(threshold) >= 0);
        run.setInputSnapshotHash(Ids.sha256(body));
        run.setInputSnapshot(body);
        run.setStatus("COMPLETED");
        run.setExecutedAt(Instant.now());
        run.setExecutedBy(access.current().id());
        store.insert(run);
        for (RegulatoryResultDetail line : details) {
            line.setRunId(run.getId());
            if (line.getId() == null) {
                line.setId(Ids.next());
            }
            store.insert(line);
        }
        RegulatoryReport report = new RegulatoryReport();
        report.setId(Ids.next());
        report.setEntityId(entity.getId());
        report.setReportCode(formula.name());
        report.setReferenceDate(request.referenceDate());
        report.setCalculationRunId(run.getId());
        report.setRuleVersionId(rule.getId());
        report.setInputSnapshotHash(run.getInputSnapshotHash());
        report.setStatus("DRAFT");
        store.insert(report);
        run.setReportId(report.getId());
        audit.record("CALCULATION", "RegulatoryCalculationRun", run.getId(), run.getInputSnapshotHash());
        return new RunView(run, details, report);
    }

    @Transactional
    public RunView get(String id) {
        access.requireAny("TREASURER", "COMPLIANCE", "AUDITOR", "VALIDATOR");
        RegulatoryCalculationRun run = store.find(RegulatoryCalculationRun.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Calcul " + id));
        access.entity(run.getEntityId());
        audit.record("CALCULATION_READ", "RegulatoryCalculationRun", run.getId(), run.getInputSnapshotHash());
        RegulatoryReport report = run.getReportId() == null ? null : store.find(RegulatoryReport.class, run.getReportId()).orElse(null);
        List<RegulatoryResultDetail> details = store.query(
                "select d from RegulatoryResultDetail d where d.runId = :id",
                RegulatoryResultDetail.class, Map.of("id", id));
        return new RunView(run, details, report);
    }

    @Transactional(readOnly = true)
    public List<RegulatoryCalculationRun> runs(String entityId) {
        return store.all(RegulatoryCalculationRun.class).stream()
                .filter(run -> access.current().canSee(run.getEntityId()))
                .filter(run -> entityId == null || entityId.isBlank() || entityId.equals(run.getEntityId()))
                .toList();
    }

    @Transactional
    public ReplayView replay(String id) {
        RunView view = get(id);
        CalculationSnapshot snapshot = read(view.run().getInputSnapshot());
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        snapshot.parameters().forEach(parameter -> values.put(parameter.code() + "|" + parameter.component(), parameter.value()));
        List<Item> items = snapshot.items().stream()
                .map(item -> new Item(item.side(), item.bucket(), item.amount(), item.sourceReference()))
                .toList();
        Outcome outcome = LiquidityCalculator.calculate(FormulaCode.valueOf(snapshot.formulaCode()), values, items);
        boolean matched = outcome.result().compareTo(view.run().getResult()) == 0
                && snapshot.ruleVersionId().equals(view.run().getRuleVersionId());
        if (!matched) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le rejeu ne reproduit pas le résultat arrêté");
        }
        return new ReplayView(view.run().getId(), view.run().getResult(), outcome.result(), true, view.run().getInputSnapshotHash());
    }

    @Transactional
    public RegulatoryReport validateReport(String id) {
        access.require("COMPLIANCE");
        RegulatoryReport report = store.find(RegulatoryReport.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "État " + id));
        access.entity(report.getEntityId());
        RegulatoryCalculationRun run = store.find(RegulatoryCalculationRun.class, report.getCalculationRunId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Calcul introuvable"));
        access.fourEyes(run.getExecutedBy());
        if (!"DRAFT".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "L'état n'est plus en brouillon");
        }
        report.setStatus("VALIDATED");
        report.setValidatedBy(access.current().id());
        report.setValidatedAt(Instant.now());
        approve("RegulatoryReport", report.getId(), "VALIDATED");
        audit.record("REPORT_VALIDATE", "RegulatoryReport", report.getId(), report.getInputSnapshotHash());
        return report;
    }

    @Transactional(readOnly = true)
    public List<RegulatoryReport> reports(String entityId) {
        return store.all(RegulatoryReport.class).stream()
                .filter(report -> access.current().canSee(report.getEntityId()))
                .filter(report -> entityId == null || entityId.isBlank() || entityId.equals(report.getEntityId()))
                .toList();
    }

    private void coherence(String entityId, FormulaCode formula, LocalDate date, List<LiquidityItem> rows, List<RegulatoryResultDetail> details) {
        List<BucketClassification> classes = store.query(
                "select c from BucketClassification c where c.entityId = :entity and c.formulaCode = :formula",
                BucketClassification.class, Map.of("entity", entityId, "formula", formula.name()));
        for (BucketClassification classification : classes) {
            if (classification.getValidFrom().isAfter(date) || (classification.getValidTo() != null && classification.getValidTo().isBefore(date))) {
                continue;
            }
            BigDecimal booked = store.query(
                    "select p from CashPosition p where p.entityId = :entity and p.accountId = :account and p.asOf = :asOf and p.kind = :kind",
                    CashPosition.class,
                    Map.of("entity", entityId, "account", classification.getSourceKey(), "asOf", date, "kind", PositionKind.BOOKED))
                    .stream().map(CashPosition::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal stock = rows.stream().filter(row -> classification.getBucket().equals(row.getBucket()))
                    .map(LiquidityItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            String state = sameMoney(booked, stock) ? "OK" : "ECART";
            details.add(detail(null, "COHERENCE_" + classification.getBucket(), "CHECK", booked, stock, money(stock.subtract(booked)), state));
        }
    }

    private RegulatoryRuleVersion resolve(FormulaCode formula, String entityId, LocalDate date) {
        List<RegulatoryRuleVersion> matched = store.all(RegulatoryRuleVersion.class).stream()
                .filter(rule -> rule.getFormulaCode() == formula)
                .filter(rule -> rule.getApprovalStatus() == ApprovalStatus.PUBLISHED)
                .filter(rule -> !rule.getValidFrom().isAfter(date))
                .filter(rule -> rule.getValidTo() == null || !rule.getValidTo().isBefore(date))
                .filter(rule -> "*".equals(rule.getEntityScope()) || entityId.equals(rule.getEntityScope()))
                .toList();
        if (matched.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Aucune règle publiée pour " + formula + " au " + date);
        }
        if (matched.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plusieurs règles publiées couvrent " + formula + " au " + date);
        }
        return matched.get(0);
    }

    private List<LiquidityItem> items(String entityId, FormulaCode formula, LocalDate date) {
        return store.query(
                "select i from LiquidityItem i where i.entityId = :entity and i.formulaCode = :formula and i.asOf = :asOf",
                LiquidityItem.class,
                Map.of("entity", entityId, "formula", formula.name(), "asOf", date));
    }

    private static Map<String, BigDecimal> parameterMap(List<RegulatoryParameter> parameters) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (RegulatoryParameter parameter : parameters) {
            values.put(parameter.getCode() + "|" + parameter.getComponent(), parameter.getValue());
        }
        return values;
    }

    private static List<Item> toItems(List<LiquidityItem> rows) {
        return rows.stream().map(row -> new Item(row.getSide(), row.getBucket(), row.getAmount(), row.getSourceReference())).toList();
    }

    private static RegulatoryResultDetail detail(String runId, String component, String side, BigDecimal amount, BigDecimal weight,
            BigDecimal contribution, String explanation) {
        RegulatoryResultDetail detail = new RegulatoryResultDetail();
        detail.setId(Ids.next());
        detail.setRunId(runId);
        detail.setComponent(component);
        detail.setSide(side);
        detail.setAmount(amount);
        detail.setWeight(weight);
        detail.setContribution(contribution);
        detail.setExplanation(explanation == null ? "" : explanation);
        return detail;
    }

    private void approve(String type, String id, String decision) {
        ComplianceApproval approval = new ComplianceApproval();
        approval.setId(Ids.next());
        approval.setSubjectType(type);
        approval.setSubjectId(id);
        approval.setApprover(access.current().id());
        approval.setDecision(decision);
        approval.setDecidedAt(Instant.now());
        store.insert(approval);
    }

    private String write(CalculationSnapshot snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private CalculationSnapshot read(String body) {
        try {
            return json.readValue(body, CalculationSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static FormulaCode formula(String value) {
        try {
            return FormulaCode.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Formule inconnue " + value);
        }
    }

    private static Jurisdiction jurisdiction(String value) {
        try {
            return Jurisdiction.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Juridiction inconnue " + value);
        }
    }

    public record RunView(RegulatoryCalculationRun run, List<RegulatoryResultDetail> details, RegulatoryReport report) {}
}
