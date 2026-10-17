package fr.nbank.treasoryflow.service;

import static fr.nbank.treasoryflow.domain.DecimalPolicy.money;

import fr.nbank.treasoryflow.api.ApiTypes.DealRequest;
import fr.nbank.treasoryflow.domain.Codes.BreachStatus;
import fr.nbank.treasoryflow.domain.Codes.DealStatus;
import fr.nbank.treasoryflow.domain.Codes.LimitAction;
import fr.nbank.treasoryflow.domain.ExposureLimit;
import fr.nbank.treasoryflow.domain.LimitBreach;
import fr.nbank.treasoryflow.domain.TreasuryDeal;
import fr.nbank.treasoryflow.domain.TreasuryProduct;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DealService {
    private static final EnumSet<DealStatus> EXPOSURE = EnumSet.of(
            DealStatus.PENDING_VALIDATION, DealStatus.VALIDATED, DealStatus.TRANSMITTED,
            DealStatus.CONFIRMED, DealStatus.SETTLED, DealStatus.RECONCILED);

    private final TreasuryStore store;
    private final Access access;
    private final AuditService audit;
    private final ReferenceService references;

    public DealService(TreasuryStore store, Access access, AuditService audit, ReferenceService references) {
        this.store = store;
        this.access = access;
        this.audit = audit;
        this.references = references;
    }

    @Transactional(readOnly = true)
    public List<TreasuryDeal> list(String entityId) {
        return store.all(TreasuryDeal.class).stream()
                .filter(deal -> access.current().canSee(deal.getEntityId()))
                .filter(deal -> entityId == null || entityId.isBlank() || entityId.equals(deal.getEntityId()))
                .toList();
    }

    @Transactional
    public TreasuryDeal create(DealRequest request) {
        access.require("TREASURER");
        access.entity(request.entityId());
        references.entity(request.entityId());
        TreasuryProduct product = store.find(TreasuryProduct.class, request.productCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Produit non autorisé"));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Produit inactif");
        }
        if (store.find(fr.nbank.treasoryflow.domain.Counterparty.class, request.counterpartyId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Contrepartie inconnue");
        }
        if (request.amount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Le montant doit être positif");
        }
        TreasuryDeal deal = new TreasuryDeal();
        deal.setId(Ids.next());
        deal.setEntityId(request.entityId());
        deal.setProductCode(request.productCode());
        deal.setCurrency(request.currency());
        deal.setCounterCurrency(request.counterCurrency());
        deal.setAmount(money(request.amount()));
        deal.setRate(request.rate());
        deal.setCounterpartyId(request.counterpartyId());
        deal.setReference(request.reference());
        deal.setTradeDate(request.tradeDate());
        deal.setValueDate(request.valueDate());
        deal.setMaturityDate(request.maturityDate());
        deal.setStatus(DealStatus.DRAFT);
        deal.setCreatedBy(access.current().id());
        deal.setCreatedAt(Instant.now());
        store.insert(deal);
        audit.record("DEAL_CREATE", "TreasuryDeal", deal.getId(), request.reference());
        return deal;
    }

    @Transactional
    public SubmitOutcome submit(String id) {
        access.require("TREASURER");
        TreasuryDeal deal = load(id);
        if (deal.getStatus() != DealStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une opération en brouillon peut être soumise");
        }
        BigDecimal exposure = exposureOf(deal).add(deal.getAmount());
        ExposureLimit limit = limitFor(deal);
        if (limit != null && exposure.compareTo(limit.getLimitAmount()) > 0) {
            LimitBreach breach = breach(deal, limit, exposure, limit.getAction() == LimitAction.BLOCK ? BreachStatus.BLOCKED : BreachStatus.OPEN);
            store.insert(breach);
            audit.record("LIMIT_BREACH", "LimitBreach", breach.getId(), breach.getReason());
            if (limit.getAction() == LimitAction.BLOCK) {
                return new SubmitOutcome(deal, breach, true);
            }
        }
        deal.setStatus(DealStatus.PENDING_VALIDATION);
        audit.record("DEAL_SUBMIT", "TreasuryDeal", deal.getId(), deal.getReference());
        return new SubmitOutcome(deal, null, false);
    }

    @Transactional
    public TreasuryDeal approve(String id) {
        access.require("VALIDATOR");
        TreasuryDeal deal = load(id);
        access.fourEyes(deal.getCreatedBy());
        if (deal.getStatus() != DealStatus.PENDING_VALIDATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "L'opération n'est pas en attente de validation");
        }
        deal.setStatus(DealStatus.VALIDATED);
        deal.setApprovedBy(access.current().id());
        deal.setApprovedAt(Instant.now());
        audit.record("DEAL_APPROVE", "TreasuryDeal", deal.getId(), deal.getApprovedBy());
        return deal;
    }

    @Transactional
    public TreasuryDeal reject(String id, String reason) {
        access.require("VALIDATOR");
        TreasuryDeal deal = load(id);
        access.fourEyes(deal.getCreatedBy());
        if (deal.getStatus() != DealStatus.PENDING_VALIDATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "L'opération n'est pas en attente de validation");
        }
        deal.setStatus(DealStatus.REJECTED);
        deal.setApprovedBy(access.current().id());
        deal.setApprovedAt(Instant.now());
        deal.setStatusReason(reason);
        audit.record("DEAL_REJECT", "TreasuryDeal", deal.getId(), reason);
        return deal;
    }

    @Transactional(readOnly = true)
    public List<LimitBreach> breaches(String entityId) {
        return store.all(LimitBreach.class).stream().filter(breach -> {
            TreasuryDeal deal = store.find(TreasuryDeal.class, breach.getDealId()).orElse(null);
            return deal != null && access.current().canSee(deal.getEntityId())
                    && (entityId == null || entityId.isBlank() || entityId.equals(deal.getEntityId()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ExposureLimit> limits(String entityId) {
        return store.all(ExposureLimit.class).stream()
                .filter(limit -> access.current().canSee(limit.getEntityId()))
                .filter(limit -> entityId == null || entityId.isBlank() || entityId.equals(limit.getEntityId()))
                .toList();
    }

    private TreasuryDeal load(String id) {
        TreasuryDeal deal = store.find(TreasuryDeal.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opération " + id));
        access.entity(deal.getEntityId());
        return deal;
    }

    private BigDecimal exposureOf(TreasuryDeal deal) {
        return store.all(TreasuryDeal.class).stream()
                .filter(other -> deal.getEntityId().equals(other.getEntityId()))
                .filter(other -> deal.getCounterpartyId().equals(other.getCounterpartyId()))
                .filter(other -> deal.getCurrency().equals(other.getCurrency()))
                .filter(other -> EXPOSURE.contains(other.getStatus()))
                .map(TreasuryDeal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ExposureLimit limitFor(TreasuryDeal deal) {
        LocalDate today = deal.getTradeDate();
        return store.all(ExposureLimit.class).stream()
                .filter(limit -> deal.getEntityId().equals(limit.getEntityId()))
                .filter(limit -> "COUNTERPARTY".equals(limit.getDimension()))
                .filter(limit -> deal.getCounterpartyId().equals(limit.getDimensionValue()))
                .filter(limit -> deal.getCurrency().equals(limit.getCurrency()))
                .filter(limit -> "ACTIVE".equals(limit.getStatus()))
                .filter(limit -> !limit.getValidFrom().isAfter(today))
                .filter(limit -> limit.getValidTo() == null || !limit.getValidTo().isBefore(today))
                .findFirst()
                .orElse(null);
    }

    private LimitBreach breach(TreasuryDeal deal, ExposureLimit limit, BigDecimal exposure, BreachStatus status) {
        LimitBreach breach = new LimitBreach();
        breach.setId(Ids.next());
        breach.setLimitId(limit.getId());
        breach.setDealId(deal.getId());
        breach.setExposure(money(exposure));
        breach.setLimitAmount(limit.getLimitAmount());
        breach.setOwner(access.current().id());
        breach.setStatus(status);
        breach.setOpenedAt(Instant.now());
        breach.setReason("Exposition " + money(exposure) + " au-delà de " + limit.getLimitAmount() + " " + limit.getCurrency());
        return breach;
    }

    public record SubmitOutcome(TreasuryDeal deal, LimitBreach breach, boolean blocked) {}
}
