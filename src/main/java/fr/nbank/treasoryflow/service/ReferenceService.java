package fr.nbank.treasoryflow.service;

import fr.nbank.treasoryflow.domain.BankAccount;
import fr.nbank.treasoryflow.domain.CashPosition;
import fr.nbank.treasoryflow.domain.Codes.PositionKind;
import fr.nbank.treasoryflow.domain.Counterparty;
import fr.nbank.treasoryflow.domain.FxRate;
import fr.nbank.treasoryflow.domain.LegalEntity;
import fr.nbank.treasoryflow.domain.TreasuryProduct;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReferenceService {
    private final TreasuryStore store;
    private final Access access;

    public ReferenceService(TreasuryStore store, Access access) {
        this.store = store;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<LegalEntity> entities() {
        return store.all(LegalEntity.class).stream().filter(entity -> access.current().canSee(entity.getId())).toList();
    }

    @Transactional(readOnly = true)
    public LegalEntity entity(String id) {
        access.entity(id);
        return store.find(LegalEntity.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entité " + id));
    }

    @Transactional(readOnly = true)
    public List<BankAccount> accounts(String entityId) {
        if (entityId != null && !entityId.isBlank()) {
            access.entity(entityId);
        }
        return store.all(BankAccount.class).stream()
                .filter(account -> access.current().canSee(account.getEntityId()))
                .filter(account -> entityId == null || entityId.isBlank() || entityId.equals(account.getEntityId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Counterparty> counterparties() {
        return store.all(Counterparty.class);
    }

    @Transactional(readOnly = true)
    public List<TreasuryProduct> products() {
        return store.all(TreasuryProduct.class);
    }

    @Transactional(readOnly = true)
    public List<FxRate> rates(LocalDate on) {
        if (on == null) {
            return store.all(FxRate.class);
        }
        return store.query("select r from FxRate r where r.rateDate = :on", FxRate.class, Map.of("on", on));
    }

    @Transactional(readOnly = true)
    public List<CashPosition> positions(String entityId, LocalDate asOf) {
        if (entityId != null && !entityId.isBlank()) {
            access.entity(entityId);
        }
        return store.all(CashPosition.class).stream()
                .filter(position -> access.current().canSee(position.getEntityId()))
                .filter(position -> entityId == null || entityId.isBlank() || entityId.equals(position.getEntityId()))
                .filter(position -> asOf == null || asOf.equals(position.getAsOf()))
                .toList();
    }

    public BankAccount requireAccount(String entityId, String accountId, String currency) {
        BankAccount account = store.find(BankAccount.class, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Compte inconnu " + accountId));
        if (!account.getEntityId().equals(entityId) || !account.getCurrency().equals(currency)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Le compte ne correspond pas à l'entité ou à la devise");
        }
        return account;
    }

    public List<CashPosition> available(String entityId, LocalDate asOf) {
        return store.query(
                "select p from CashPosition p where p.entityId = :entity and p.asOf = :asOf and p.kind = :kind",
                CashPosition.class,
                Map.of("entity", entityId, "asOf", asOf, "kind", PositionKind.AVAILABLE));
    }
}
