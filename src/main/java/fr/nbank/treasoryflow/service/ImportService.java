package fr.nbank.treasoryflow.service;

import static fr.nbank.treasoryflow.domain.DecimalPolicy.money;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.nbank.treasoryflow.api.ApiTypes.BalanceLine;
import fr.nbank.treasoryflow.api.ApiTypes.ImportRequest;
import fr.nbank.treasoryflow.api.ApiTypes.ImportView;
import fr.nbank.treasoryflow.api.ApiTypes.MovementLine;
import fr.nbank.treasoryflow.domain.CashPosition;
import fr.nbank.treasoryflow.domain.Codes.ImportStatus;
import fr.nbank.treasoryflow.domain.Codes.PositionKind;
import fr.nbank.treasoryflow.domain.ImportBatch;
import fr.nbank.treasoryflow.domain.SourceMovement;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportService {
    private final TreasuryStore store;
    private final Access access;
    private final AuditService audit;
    private final ReferenceService references;
    private final ObjectMapper json;

    public ImportService(TreasuryStore store, Access access, AuditService audit, ReferenceService references, ObjectMapper json) {
        this.store = store;
        this.access = access;
        this.audit = audit;
        this.references = references;
        this.json = json;
    }

    @Transactional
    public ImportView ingest(ImportRequest request) {
        access.require("TREASURER");
        if ((request.balances() == null || request.balances().isEmpty())
                && (request.movements() == null || request.movements().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Lot vide");
        }
        String hash = Ids.sha256(canonical(request));
        ImportBatch existing = store.first(
                "select b from ImportBatch b where b.sourceSystem = :source and b.externalBatchId = :batch",
                ImportBatch.class,
                Map.of("source", request.sourceSystem(), "batch", request.externalBatchId())).orElse(null);
        if (existing != null) {
            if (!existing.getPayloadHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Lot déjà appliqué avec un autre contenu : " + request.externalBatchId());
            }
            return new ImportView(existing.getId(), existing.getStatus().name(), existing.getAccepted(), existing.getRejected(), true, hash);
        }
        ImportBatch batch = new ImportBatch();
        batch.setId(Ids.next());
        batch.setSourceSystem(request.sourceSystem());
        batch.setExternalBatchId(request.externalBatchId());
        batch.setPayloadHash(hash);
        batch.setReceivedAt(Instant.now());
        batch.setReceivedBy(access.current().id());
        int accepted = 0;
        int rejected = 0;
        if (request.balances() != null) {
            for (BalanceLine line : request.balances()) {
                if (applyBalance(request.sourceSystem(), line)) {
                    accepted++;
                } else {
                    rejected++;
                }
            }
        }
        if (request.movements() != null) {
            for (MovementLine line : request.movements()) {
                if (applyMovement(batch, request.sourceSystem(), line)) {
                    accepted++;
                } else {
                    rejected++;
                }
            }
        }
        batch.setAccepted(accepted);
        batch.setRejected(rejected);
        batch.setStatus(rejected == 0 ? ImportStatus.APPLIED : accepted == 0 ? ImportStatus.REJECTED : ImportStatus.PARTIAL);
        store.insert(batch);
        audit.record("IMPORT", "ImportBatch", batch.getId(), request.externalBatchId() + " hash=" + hash);
        return new ImportView(batch.getId(), batch.getStatus().name(), accepted, rejected, false, hash);
    }

    @Transactional(readOnly = true)
    public ImportBatch get(String id) {
        access.requireAny("TREASURER", "AUDITOR", "COMPLIANCE");
        return store.find(ImportBatch.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lot " + id));
    }

    @Transactional(readOnly = true)
    public List<SourceMovement> movements(String batchId) {
        access.requireAny("TREASURER", "AUDITOR", "COMPLIANCE");
        return store.query("select m from SourceMovement m where m.batchId = :batch", SourceMovement.class, Map.of("batch", batchId));
    }

    private boolean applyBalance(String sourceSystem, BalanceLine line) {
        try {
            access.entity(line.entityId());
            references.requireAccount(line.entityId(), line.accountId(), line.currency());
        } catch (ResponseStatusException ex) {
            return false;
        }
        upsert(line, PositionKind.BOOKED, line.bookedAmount(), sourceSystem);
        if (line.availableAmount() != null) {
            upsert(line, PositionKind.AVAILABLE, line.availableAmount(), sourceSystem);
        }
        return true;
    }

    private void upsert(BalanceLine line, PositionKind kind, java.math.BigDecimal amount, String sourceSystem) {
        CashPosition position = store.first(
                "select p from CashPosition p where p.entityId = :entity and p.accountId = :account and p.currency = :currency and p.asOf = :asOf and p.kind = :kind and p.intercompanyPairId is null",
                CashPosition.class,
                Map.of("entity", line.entityId(), "account", line.accountId(), "currency", line.currency(), "asOf", line.asOf(), "kind", kind))
                .orElse(null);
        if (position == null) {
            position = new CashPosition();
            position.setId(Ids.next());
            position.setEntityId(line.entityId());
            position.setAccountId(line.accountId());
            position.setCurrency(line.currency());
            position.setAsOf(line.asOf());
            position.setKind(kind);
            position.setSourceSystem(sourceSystem);
            position.setSourceReference(line.sourceReference());
            position.setAmount(money(amount));
            position.setUpdatedAt(Instant.now());
            store.insert(position);
            return;
        }
        String previous = position.getAmount().toPlainString();
        position.setAmount(money(amount));
        position.setSourceSystem(sourceSystem);
        position.setSourceReference(line.sourceReference());
        position.setUpdatedAt(Instant.now());
        audit.record("BALANCE_CORRECTION", "CashPosition", position.getId(), previous + " -> " + position.getAmount().toPlainString());
    }

    private boolean applyMovement(ImportBatch batch, String sourceSystem, MovementLine line) {
        try {
            access.entity(line.entityId());
            references.requireAccount(line.entityId(), line.accountId(), line.currency());
        } catch (ResponseStatusException ex) {
            return false;
        }
        boolean duplicate = store.first(
                "select m from SourceMovement m where m.sourceSystem = :source and m.externalId = :external",
                SourceMovement.class,
                Map.of("source", sourceSystem, "external", line.externalId())).isPresent();
        SourceMovement movement = new SourceMovement();
        movement.setId(Ids.next());
        movement.setBatchId(batch.getId());
        movement.setSourceSystem(sourceSystem);
        movement.setExternalId(line.externalId());
        movement.setEntityId(line.entityId());
        movement.setAccountId(line.accountId());
        movement.setCurrency(line.currency());
        movement.setAmount(money(line.amount()));
        movement.setValueDate(line.valueDate());
        movement.setBookingDate(line.bookingDate());
        movement.setReference(line.reference());
        if (duplicate) {
            movement.setStatus("REJECTED");
            movement.setRejectReason("Mouvement déjà importé");
            movement.setExternalId(line.externalId() + "#rejected-" + movement.getId());
            store.insert(movement);
            return false;
        }
        movement.setStatus("IMPORTED");
        store.insert(movement);
        return true;
    }

    private String canonical(ImportRequest request) {
        try {
            return json.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
