package fr.nbank.treasoryflow.service;

import static fr.nbank.treasoryflow.domain.DecimalPolicy.money;

import fr.nbank.treasoryflow.api.ApiTypes.PaymentRequest;
import fr.nbank.treasoryflow.domain.BankAccount;
import fr.nbank.treasoryflow.domain.CashPool;
import fr.nbank.treasoryflow.domain.CashPoolMember;
import fr.nbank.treasoryflow.domain.CashPosition;
import fr.nbank.treasoryflow.domain.Codes.DealStatus;
import fr.nbank.treasoryflow.domain.Codes.PaymentPurpose;
import fr.nbank.treasoryflow.domain.Codes.PoolType;
import fr.nbank.treasoryflow.domain.Codes.PositionKind;
import fr.nbank.treasoryflow.domain.ForecastFlow;
import fr.nbank.treasoryflow.domain.LegalEntity;
import fr.nbank.treasoryflow.domain.PaymentOrder;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.math.BigDecimal;
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
public class CashManagementService {
    private final TreasuryStore store;
    private final Access access;
    private final AuditService audit;
    private final ReferenceService references;

    public CashManagementService(TreasuryStore store, Access access, AuditService audit, ReferenceService references) {
        this.store = store;
        this.access = access;
        this.audit = audit;
        this.references = references;
    }

    @Transactional(readOnly = true)
    public Worksheet worksheet(String entityId, LocalDate asOf) {
        access.entity(entityId);
        Map<String, LineBuilder> lines = new LinkedHashMap<>();
        for (CashPosition position : store.all(CashPosition.class)) {
            if (!entityId.equals(position.getEntityId()) || !asOf.equals(position.getAsOf())) {
                continue;
            }
            LineBuilder line = lines.computeIfAbsent(position.getAccountId(), id -> new LineBuilder(id, position.getCurrency()));
            if (position.getKind() == PositionKind.BOOKED) {
                line.booked = line.booked.add(position.getAmount());
            }
            if (position.getKind() == PositionKind.AVAILABLE) {
                line.available = line.available.add(position.getAmount());
            }
        }
        for (ForecastFlow flow : store.all(ForecastFlow.class)) {
            if (!entityId.equals(flow.getEntityId()) || !asOf.equals(flow.getValueDate())) {
                continue;
            }
            LineBuilder line = lines.computeIfAbsent(flow.getAccountId(), id -> new LineBuilder(id, flow.getCurrency()));
            if (flow.getAmount().signum() >= 0) {
                line.inflows = line.inflows.add(flow.getAmount());
            } else {
                line.outflows = line.outflows.add(flow.getAmount().abs());
            }
        }
        List<WorksheetLine> rows = lines.values().stream().map(LineBuilder::build).toList();
        BigDecimal opening = rows.stream().map(WorksheetLine::available).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DayPoint> horizon = new ArrayList<>();
        BigDecimal closing = opening;
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = asOf.plusDays(offset);
            BigDecimal inflow = BigDecimal.ZERO;
            BigDecimal outflow = BigDecimal.ZERO;
            for (ForecastFlow flow : store.all(ForecastFlow.class)) {
                if (!entityId.equals(flow.getEntityId()) || !date.equals(flow.getValueDate())) {
                    continue;
                }
                if (flow.getAmount().signum() >= 0) {
                    inflow = inflow.add(flow.getAmount());
                } else {
                    outflow = outflow.add(flow.getAmount().abs());
                }
            }
            closing = money(closing.add(inflow).subtract(outflow));
            horizon.add(new DayPoint(date, money(inflow), money(outflow), closing));
        }
        return new Worksheet(entityId, asOf, rows, horizon);
    }

    @Transactional(readOnly = true)
    public List<PaymentOrder> payments(String entityId) {
        return store.all(PaymentOrder.class).stream()
                .filter(payment -> access.current().canSee(payment.getEntityId()))
                .filter(payment -> entityId == null || entityId.isBlank() || entityId.equals(payment.getEntityId()))
                .toList();
    }

    @Transactional
    public PaymentOrder createPayment(PaymentRequest request) {
        access.require("TREASURER");
        access.entity(request.entityId());
        BankAccount account = store.find(BankAccount.class, request.debitAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Compte débiteur inconnu"));
        if (!request.entityId().equals(account.getEntityId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Le compte n'appartient pas à l'entité");
        }
        if (!request.currency().equals(account.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "La devise du paiement doit être celle du compte");
        }
        if (request.amount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Le montant doit être positif");
        }
        PaymentPurpose purpose = purpose(request.purpose());
        PaymentOrder payment = new PaymentOrder();
        payment.setId(Ids.next());
        payment.setEntityId(request.entityId());
        payment.setDebitAccountId(request.debitAccountId());
        payment.setBeneficiaryName(request.beneficiaryName());
        payment.setBeneficiaryEntityId(blankToNull(request.beneficiaryEntityId()));
        payment.setCurrency(request.currency());
        payment.setAmount(money(request.amount()));
        payment.setValueDate(request.valueDate());
        payment.setPurpose(purpose);
        payment.setStatus(DealStatus.DRAFT);
        payment.setCreatedBy(access.current().id());
        payment.setCreatedAt(Instant.now());
        store.insert(payment);
        audit.record("PAYMENT_CREATE", "PaymentOrder", payment.getId(), purpose.name());
        return payment;
    }

    @Transactional
    public PaymentSubmit submitPayment(String id) {
        access.require("TREASURER");
        PaymentOrder payment = loadPayment(id);
        if (payment.getStatus() != DealStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seul un paiement en brouillon peut être soumis");
        }
        String block = transferBlock(payment);
        if (block != null) {
            payment.setStatusReason(block);
            audit.record("PAYMENT_BLOCKED", "PaymentOrder", payment.getId(), block);
            return new PaymentSubmit(payment, true, block);
        }
        payment.setStatus(DealStatus.PENDING_VALIDATION);
        payment.setStatusReason(null);
        audit.record("PAYMENT_SUBMIT", "PaymentOrder", payment.getId(), payment.getPurpose().name());
        return new PaymentSubmit(payment, false, null);
    }

    @Transactional
    public PaymentOrder approvePayment(String id) {
        access.require("VALIDATOR");
        PaymentOrder payment = loadPayment(id);
        access.fourEyes(payment.getCreatedBy());
        if (payment.getStatus() != DealStatus.PENDING_VALIDATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le paiement n'est pas en attente de validation");
        }
        payment.setStatus(DealStatus.VALIDATED);
        payment.setApprovedBy(access.current().id());
        payment.setApprovedAt(Instant.now());
        audit.record("PAYMENT_APPROVE", "PaymentOrder", payment.getId(), payment.getApprovedBy());
        return payment;
    }

    @Transactional(readOnly = true)
    public List<CashPool> pools() {
        return store.all(CashPool.class).stream()
                .filter(pool -> access.current().canSee(pool.getHeaderEntityId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Sweep sweep(String poolId, LocalDate asOf) {
        access.require("TREASURER");
        CashPool pool = store.find(CashPool.class, poolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pool " + poolId));
        access.entity(pool.getHeaderEntityId());
        List<SweepLine> lines = new ArrayList<>();
        for (CashPoolMember member : store.all(CashPoolMember.class)) {
            if (!poolId.equals(member.getPoolId()) || member.getAccountId().equals(pool.getHeaderAccountId())) {
                continue;
            }
            access.entity(member.getEntityId());
            BigDecimal available = available(member.getEntityId(), member.getAccountId(), asOf);
            if (pool.getType() == PoolType.NOTIONAL) {
                lines.add(new SweepLine(member.getEntityId(), member.getAccountId(), available, money(BigDecimal.ZERO), false,
                        "Pool notionnel : la position est agrégée, aucun virement n'est proposé."));
                continue;
            }
            LegalEntity entity = references.entity(member.getEntityId());
            if (!member.getEntityId().equals(pool.getHeaderEntityId()) && entity.isTransferRestricted()) {
                lines.add(new SweepLine(member.getEntityId(), member.getAccountId(), available, money(BigDecimal.ZERO), false,
                        entity.getTransferRestrictionReason()));
                continue;
            }
            BigDecimal sweepAmount = available.signum() > 0 ? available : BigDecimal.ZERO;
            lines.add(new SweepLine(member.getEntityId(), member.getAccountId(), available, money(sweepAmount), sweepAmount.signum() > 0,
                    sweepAmount.signum() > 0 ? "Excédent à niveler vers le compte pivot." : "Pas d'excédent à niveler."));
        }
        BigDecimal executable = lines.stream().filter(SweepLine::executable).map(SweepLine::sweepAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal blocked = lines.stream().filter(line -> !line.executable() && line.available().signum() > 0).map(SweepLine::available)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Sweep(pool.getId(), pool.getType(), pool.getCurrency(), lines, money(executable), money(blocked));
    }

    private String transferBlock(PaymentOrder payment) {
        if (payment.getPurpose() != PaymentPurpose.INTERCOMPANY && payment.getPurpose() != PaymentPurpose.TREASURY_TRANSFER) {
            return null;
        }
        if (payment.getBeneficiaryEntityId() == null || payment.getBeneficiaryEntityId().equals(payment.getEntityId())) {
            return null;
        }
        LegalEntity entity = references.entity(payment.getEntityId());
        if (!entity.isTransferRestricted()) {
            return null;
        }
        return entity.getTransferRestrictionReason();
    }

    private BigDecimal available(String entityId, String accountId, LocalDate asOf) {
        return store.all(CashPosition.class).stream()
                .filter(position -> entityId.equals(position.getEntityId()))
                .filter(position -> accountId.equals(position.getAccountId()))
                .filter(position -> asOf.equals(position.getAsOf()))
                .filter(position -> position.getKind() == PositionKind.AVAILABLE)
                .map(CashPosition::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PaymentOrder loadPayment(String id) {
        PaymentOrder payment = store.find(PaymentOrder.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement " + id));
        access.entity(payment.getEntityId());
        return payment;
    }

    private static PaymentPurpose purpose(String value) {
        try {
            return PaymentPurpose.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Objet de paiement inconnu");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class LineBuilder {
        private final String accountId;
        private final String currency;
        private BigDecimal booked = BigDecimal.ZERO;
        private BigDecimal available = BigDecimal.ZERO;
        private BigDecimal inflows = BigDecimal.ZERO;
        private BigDecimal outflows = BigDecimal.ZERO;

        private LineBuilder(String accountId, String currency) {
            this.accountId = accountId;
            this.currency = currency;
        }

        private WorksheetLine build() {
            return new WorksheetLine(accountId, currency, money(booked), money(available), money(inflows), money(outflows),
                    money(available.add(inflows).subtract(outflows)));
        }
    }

    public record WorksheetLine(String accountId, String currency, BigDecimal booked, BigDecimal available, BigDecimal inflows,
            BigDecimal outflows, BigDecimal projected) {}

    public record DayPoint(LocalDate date, BigDecimal inflow, BigDecimal outflow, BigDecimal closing) {}

    public record Worksheet(String entityId, LocalDate asOf, List<WorksheetLine> lines, List<DayPoint> horizon) {}

    public record PaymentSubmit(PaymentOrder payment, boolean blocked, String reason) {}

    public record SweepLine(String entityId, String accountId, BigDecimal available, BigDecimal sweepAmount, boolean executable, String reason) {}

    public record Sweep(String poolId, PoolType type, String currency, List<SweepLine> lines, BigDecimal executableAmount, BigDecimal blockedAmount) {}
}
