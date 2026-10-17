package fr.nbank.treasoryflow.service;

import fr.nbank.treasoryflow.domain.Codes.DealStatus;
import fr.nbank.treasoryflow.domain.ReconciliationBreak;
import fr.nbank.treasoryflow.domain.SourceMovement;
import fr.nbank.treasoryflow.domain.TreasuryDeal;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
    private final TreasuryStore store;
    private final Access access;
    private final AuditService audit;

    public ReconciliationService(TreasuryStore store, Access access, AuditService audit) {
        this.store = store;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public List<ReconciliationBreak> run(String entityId) {
        access.require("TREASURER");
        access.entity(entityId);
        List<TreasuryDeal> deals = store.all(TreasuryDeal.class).stream()
                .filter(deal -> entityId.equals(deal.getEntityId()))
                .filter(deal -> deal.getStatus() != DealStatus.DRAFT && deal.getStatus() != DealStatus.REJECTED && deal.getStatus() != DealStatus.CANCELLED)
                .toList();
        List<SourceMovement> movements = store.all(SourceMovement.class).stream()
                .filter(movement -> entityId.equals(movement.getEntityId()))
                .filter(movement -> "IMPORTED".equals(movement.getStatus()))
                .toList();
        for (SourceMovement movement : movements) {
            boolean matched = deals.stream().anyMatch(deal -> deal.getReference().equals(movement.getReference())
                    && deal.getCurrency().equals(movement.getCurrency())
                    && deal.getAmount().compareTo(movement.getAmount().abs()) == 0);
            if (matched) {
                movement.setStatus("MATCHED");
                continue;
            }
            boolean known = store.all(ReconciliationBreak.class).stream().anyMatch(item -> movement.getId().equals(item.getMovementId()));
            if (!known) {
                ReconciliationBreak gap = new ReconciliationBreak();
                gap.setId(Ids.next());
                gap.setEntityId(entityId);
                gap.setMovementId(movement.getId());
                gap.setCurrency(movement.getCurrency());
                gap.setAmount(movement.getAmount());
                gap.setValueDate(movement.getValueDate());
                gap.setStatus("OPEN");
                gap.setReason("Aucun règlement interne pour la référence " + movement.getReference());
                store.insert(gap);
            }
        }
        audit.record("RECONCILIATION", "LegalEntity", entityId, "rapprochement");
        return breaks(entityId);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationBreak> breaks(String entityId) {
        if (entityId != null && !entityId.isBlank()) {
            access.entity(entityId);
        }
        return store.all(ReconciliationBreak.class).stream()
                .filter(item -> access.current().canSee(item.getEntityId()))
                .filter(item -> entityId == null || entityId.isBlank() || entityId.equals(item.getEntityId()))
                .toList();
    }
}
