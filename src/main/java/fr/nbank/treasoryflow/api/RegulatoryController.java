package fr.nbank.treasoryflow.api;

import fr.nbank.treasoryflow.api.ApiTypes.CalculationRequest;
import fr.nbank.treasoryflow.api.ApiTypes.ItemRequest;
import fr.nbank.treasoryflow.api.ApiTypes.RuleDraft;
import fr.nbank.treasoryflow.service.AuditService;
import fr.nbank.treasoryflow.service.RegulatoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegulatoryController {
    private final RegulatoryService regulatory;
    private final AuditService audit;

    public RegulatoryController(RegulatoryService regulatory, AuditService audit) {
        this.regulatory = regulatory;
        this.audit = audit;
    }

    @GetMapping("/api/v1/regulatory/jurisdictions")
    public Object jurisdictions() {
        return regulatory.jurisdictions();
    }

    @GetMapping("/api/v1/regulatory/profiles")
    public Object profiles() {
        return regulatory.profiles();
    }

    @GetMapping("/api/v1/regulatory/rules")
    public Object rules() {
        return regulatory.rules();
    }

    @GetMapping("/api/v1/regulatory/rules/{id}/parameters")
    public Object parameters(@PathVariable String id) {
        return regulatory.parameters(id);
    }

    @PostMapping("/api/v1/regulatory/rules")
    public Object create(@Valid @RequestBody RuleDraft draft) {
        return regulatory.create(draft);
    }

    @PostMapping("/api/v1/regulatory/rules/{id}/publish")
    public Object publish(@PathVariable String id) {
        return regulatory.publish(id);
    }

    @PostMapping("/api/v1/regulatory/items")
    public Object item(@Valid @RequestBody ItemRequest request) {
        return regulatory.addItem(request);
    }

    @PostMapping("/api/v1/regulatory/calculations")
    public Object run(@Valid @RequestBody CalculationRequest request) {
        return regulatory.run(request);
    }

    @GetMapping("/api/v1/regulatory/calculations/{id}")
    public Object get(@PathVariable String id) {
        return regulatory.get(id);
    }

    @GetMapping("/api/v1/regulatory/calculations")
    public Object runs(@RequestParam(required = false) String entityId) {
        return regulatory.runs(entityId);
    }

    @PostMapping("/api/v1/regulatory/calculations/{id}/replay")
    public Object replay(@PathVariable String id) {
        return regulatory.replay(id);
    }

    @PostMapping("/api/v1/regulatory/reports/{id}/validate")
    public Object validate(@PathVariable String id) {
        return regulatory.validateReport(id);
    }

    @GetMapping("/api/v1/regulatory/reports")
    public Object reports(@RequestParam(required = false) String entityId) {
        return regulatory.reports(entityId);
    }

    @GetMapping("/api/v1/audit")
    public Object audit() {
        return audit.latest();
    }
}
