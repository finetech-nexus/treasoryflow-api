package fr.nbank.treasoryflow.service;

import fr.nbank.treasoryflow.domain.AuditEvent;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final TreasuryStore store;
    private final Access access;

    public AuditService(TreasuryStore store, Access access) {
        this.store = store;
        this.access = access;
    }

    @Transactional
    public void record(String action, String subjectType, String subjectId, String detail) {
        AuditEvent event = new AuditEvent();
        event.setId(Ids.next());
        event.setOccurredAt(Instant.now());
        event.setActor(access.current().id());
        event.setAction(action);
        event.setSubjectType(subjectType);
        event.setSubjectId(subjectId);
        event.setDetail(detail == null ? "" : detail.substring(0, Math.min(detail.length(), 1000)));
        store.insert(event);
    }

    @Transactional
    public void recordAs(String actor, String action, String subjectType, String subjectId, String detail) {
        AuditEvent event = new AuditEvent();
        event.setId(Ids.next());
        event.setOccurredAt(Instant.now());
        event.setActor(actor);
        event.setAction(action);
        event.setSubjectType(subjectType);
        event.setSubjectId(subjectId);
        event.setDetail(detail == null ? "" : detail.substring(0, Math.min(detail.length(), 1000)));
        store.insert(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> latest() {
        access.requireAny("AUDITOR", "COMPLIANCE", "VALIDATOR");
        return store.query("select e from AuditEvent e order by e.occurredAt desc", AuditEvent.class, java.util.Map.of())
                .stream().limit(200).toList();
    }
}
