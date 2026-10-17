package fr.nbank.treasoryflow.api;

import fr.nbank.treasoryflow.api.ApiTypes.DealRequest;
import fr.nbank.treasoryflow.api.ApiTypes.FlowRequest;
import fr.nbank.treasoryflow.api.ApiTypes.ImportRequest;
import fr.nbank.treasoryflow.api.ApiTypes.ProjectRequest;
import fr.nbank.treasoryflow.domain.BusinessClock;
import fr.nbank.treasoryflow.domain.CashPosition;
import fr.nbank.treasoryflow.domain.Codes.DealStatus;
import fr.nbank.treasoryflow.domain.Codes.PositionKind;
import fr.nbank.treasoryflow.domain.TreasuryDeal;
import fr.nbank.treasoryflow.service.DealService;
import fr.nbank.treasoryflow.service.DealService.SubmitOutcome;
import fr.nbank.treasoryflow.service.ForecastService;
import fr.nbank.treasoryflow.service.GroupService;
import fr.nbank.treasoryflow.service.ImportService;
import fr.nbank.treasoryflow.service.ReconciliationService;
import fr.nbank.treasoryflow.service.ReferenceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TreasuryController {
    private final ReferenceService references;
    private final DealService deals;
    private final ImportService imports;
    private final ForecastService forecasts;
    private final GroupService group;
    private final ReconciliationService reconciliation;

    public TreasuryController(ReferenceService references, DealService deals, ImportService imports,
            ForecastService forecasts, GroupService group, ReconciliationService reconciliation) {
        this.references = references;
        this.deals = deals;
        this.imports = imports;
        this.forecasts = forecasts;
        this.group = group;
        this.reconciliation = reconciliation;
    }

    @GetMapping("/api/v1/dashboard")
    public Map<String, Object> dashboard() {
        List<CashPosition> positions = references.positions(null, BusinessClock.REPORTING_DATE);
        List<TreasuryDeal> book = deals.list(null);
        long available = positions.stream()
                .filter(position -> position.getKind() == PositionKind.AVAILABLE && position.getIntercompanyPairId() == null)
                .count();
        long pending = book.stream().filter(deal -> deal.getStatus() == DealStatus.PENDING_VALIDATION).count();
        long validated = book.stream().filter(deal -> deal.getStatus() == DealStatus.VALIDATED).count();
        return Map.of(
                "asOf", BusinessClock.REPORTING_DATE,
                "entities", references.entities(),
                "positions", positions,
                "availablePositions", available,
                "pendingDeals", pending,
                "validatedDeals", validated,
                "published", references.publishedIndicators(),
                "disclaimer", "Identité publique BIAT et états consolidés publiés au 31 décembre 2025. Les positions du scénario de calcul ne sont pas le bilan de la banque. Ce n'est pas une certification réglementaire.");
    }

    @GetMapping("/api/v1/entities")
    public Object entities() {
        return references.entities();
    }

    @GetMapping("/api/v1/accounts")
    public Object accounts(@RequestParam(required = false) String entityId) {
        return references.accounts(entityId);
    }

    @GetMapping("/api/v1/counterparties")
    public Object counterparties() {
        return references.counterparties();
    }

    @GetMapping("/api/v1/products")
    public Object products() {
        return references.products();
    }

    @GetMapping("/api/v1/rates")
    public Object rates(@RequestParam(required = false) LocalDate on) {
        return references.rates(on);
    }

    @GetMapping("/api/v1/positions")
    public Object positions(@RequestParam(required = false) String entityId, @RequestParam(required = false) LocalDate asOf) {
        return references.positions(entityId, asOf == null ? BusinessClock.REPORTING_DATE : asOf);
    }

    @GetMapping("/api/v1/deals")
    public Object deals(@RequestParam(required = false) String entityId) {
        return deals.list(entityId);
    }

    @PostMapping("/api/v1/deals")
    public Object createDeal(@Valid @RequestBody DealRequest request) {
        return deals.create(request);
    }

    @PostMapping("/api/v1/deals/{id}/submit")
    public ResponseEntity<SubmitOutcome> submit(@PathVariable String id) {
        SubmitOutcome outcome = deals.submit(id);
        return ResponseEntity.status(outcome.blocked() ? 409 : 200).body(outcome);
    }

    @PostMapping("/api/v1/deals/{id}/approve")
    public Object approve(@PathVariable String id) {
        return deals.approve(id);
    }

    @PostMapping("/api/v1/deals/{id}/reject")
    public Object reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return deals.reject(id, body == null ? "" : body.getOrDefault("reason", ""));
    }

    @GetMapping("/api/v1/limits")
    public Object limits(@RequestParam(required = false) String entityId) {
        return deals.limits(entityId);
    }

    @GetMapping("/api/v1/breaches")
    public Object breaches(@RequestParam(required = false) String entityId) {
        return deals.breaches(entityId);
    }

    @PostMapping("/api/v1/imports/core-banking")
    public Object ingest(@Valid @RequestBody ImportRequest request) {
        return imports.ingest(request);
    }

    @GetMapping("/api/v1/imports/{id}")
    public Object batch(@PathVariable String id) {
        return Map.of("batch", imports.get(id), "movements", imports.movements(id));
    }

    @PostMapping("/api/v1/forecasts/flows")
    public Object addFlow(@Valid @RequestBody FlowRequest request) {
        return forecasts.addFlow(request);
    }

    @PostMapping("/api/v1/forecasts/project")
    public Object project(@Valid @RequestBody ProjectRequest request) {
        return forecasts.project(request);
    }

    @GetMapping("/api/v1/alm/gaps")
    public Object gaps(@RequestParam String entityId, @RequestParam(required = false) LocalDate asOf) {
        return forecasts.gaps(entityId, asOf == null ? BusinessClock.REPORTING_DATE : asOf);
    }

    @PostMapping("/api/v1/funding/analyse")
    public Object analyse(@RequestParam(defaultValue = "GRP-TN-EU") String scopeId, @RequestParam(required = false) LocalDate asOf) {
        return forecasts.analyse(scopeId, asOf == null ? BusinessClock.REPORTING_DATE : asOf);
    }

    @GetMapping("/api/v1/funding/recommendations")
    public Object recommendations(@RequestParam(required = false) String entityId) {
        return forecasts.recommendations(entityId);
    }

    @PostMapping("/api/v1/funding/recommendations/{id}/approve")
    public Object approveRecommendation(@PathVariable String id) {
        return forecasts.approve(id);
    }

    @GetMapping("/api/v1/group/scopes")
    public Object scopes() {
        return group.scopes();
    }

    @GetMapping("/api/v1/group/flows")
    public Object flows(@RequestParam(defaultValue = "GRP-TN-EU") String scopeId) {
        return group.flows(scopeId);
    }

    @PostMapping("/api/v1/group/consolidations")
    public Object consolidate(@RequestParam(defaultValue = "GRP-TN-EU") String scopeId, @RequestParam(required = false) LocalDate asOf) {
        return group.consolidate(scopeId, asOf == null ? BusinessClock.REPORTING_DATE : asOf);
    }

    @PostMapping("/api/v1/reconciliation/run")
    public Object reconcile(@RequestParam String entityId) {
        return reconciliation.run(entityId);
    }

    @GetMapping("/api/v1/reconciliation/breaks")
    public Object breaks(@RequestParam(required = false) String entityId) {
        return reconciliation.breaks(entityId);
    }

    @GetMapping("/api/v1/controls")
    public List<Map<String, String>> controls() {
        return List.of(
                Map.of("id", "REC-SEC-001", "module", "M10", "statement", "L'auteur d'une opération ne peut pas l'approuver."),
                Map.of("id", "REC-INT-001", "module", "M02", "statement", "La réexécution d'un lot ne duplique ni ne perd les mouvements."),
                Map.of("id", "REC-AUD-001", "module", "M09", "statement", "Un résultat réglementaire se rejoue depuis son instantané de données et de paramètres."),
                Map.of("id", "JUR-ISO", "module", "TN-002", "statement", "Une formule européenne n'est pas appliquée à une entité tunisienne, ni l'inverse."),
                Map.of("id", "REC-GRP-001", "module", "GRP-002", "statement", "Les flux intragroupe équilibrés sont éliminés une seule fois."),
                Map.of("id", "REC-GRP-002", "module", "GRP-003", "statement", "Une liquidité sous restriction n'est pas proposée comme librement transférable."));
    }

    @GetMapping("/api/v1/interfaces")
    public List<Map<String, String>> interfaces() {
        return List.of(
                Map.of("code", "CORE_BANKING", "protocol", "API", "status", "ACTIVE", "activation", "Soldes et mouvements du core, avec lot idempotent."),
                Map.of("code", "ACCOUNTING", "protocol", "API", "status", "INACTIVE", "activation", "Les écritures définitives restent transmises au système comptable officiel."),
                Map.of("code", "SEPA", "protocol", "ISO 20022", "status", "INACTIVE", "activation", "Activé seulement si le périmètre, les accès et les obligations de l'entité l'exigent."),
                Map.of("code", "TARGET", "protocol", "ISO 20022", "status", "INACTIVE", "activation", "Activé seulement pour les entités qui participent à l'infrastructure."),
                Map.of("code", "SWIFT", "protocol", "MT/MX", "status", "INACTIVE", "activation", "Activé seulement si le périmètre correspondant l'exige."),
                Map.of("code", "SIBTEL", "protocol", "Fichier", "status", "INACTIVE", "activation", "Paiements tunisiens : à activer selon les systèmes réellement utilisés."),
                Map.of("code", "IAM", "protocol", "OIDC", "status", "SEAM", "activation", "L'API attend l'identité propagée par la passerelle. Le sélecteur de la console est un bac à sable."));
    }
}
