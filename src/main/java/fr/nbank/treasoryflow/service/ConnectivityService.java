package fr.nbank.treasoryflow.service;

import static fr.nbank.treasoryflow.domain.DecimalPolicy.money;

import fr.nbank.treasoryflow.api.ApiTypes.BalanceLine;
import fr.nbank.treasoryflow.api.ApiTypes.ImportRequest;
import fr.nbank.treasoryflow.api.ApiTypes.ImportView;
import fr.nbank.treasoryflow.api.ApiTypes.MovementLine;
import fr.nbank.treasoryflow.api.ApiTypes.RouteRequest;
import fr.nbank.treasoryflow.domain.BankAccount;
import fr.nbank.treasoryflow.domain.Codes.DealStatus;
import fr.nbank.treasoryflow.domain.Codes.Jurisdiction;
import fr.nbank.treasoryflow.domain.Codes.PaymentPurpose;
import fr.nbank.treasoryflow.domain.ConnectivityChannel;
import fr.nbank.treasoryflow.domain.IntegrationMessage;
import fr.nbank.treasoryflow.domain.LegalEntity;
import fr.nbank.treasoryflow.domain.PaymentOrder;
import fr.nbank.treasoryflow.domain.PaymentProfile;
import fr.nbank.treasoryflow.domain.PlatformParameter;
import fr.nbank.treasoryflow.persist.TreasuryStore;
import fr.nbank.treasoryflow.security.Access;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConnectivityService {
    private final TreasuryStore store;
    private final Access access;
    private final AuditService audit;
    private final ImportService imports;

    public ConnectivityService(TreasuryStore store, Access access, AuditService audit, ImportService imports) {
        this.store = store;
        this.access = access;
        this.audit = audit;
        this.imports = imports;
    }

    @Transactional(readOnly = true)
    public List<ConnectivityChannel> channels() {
        access.requireAny("TREASURER", "VALIDATOR", "COMPLIANCE", "AUDITOR");
        return store.all(ConnectivityChannel.class).stream()
                .sorted(Comparator.comparing(ConnectivityChannel::getCode))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformParameter> parameters() {
        access.requireAny("TREASURER", "VALIDATOR", "COMPLIANCE", "AUDITOR");
        return store.all(PlatformParameter.class).stream()
                .sorted(Comparator.comparing(PlatformParameter::getCode))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentProfile> profiles() {
        access.requireAny("TREASURER", "VALIDATOR", "COMPLIANCE", "AUDITOR");
        return store.all(PaymentProfile.class).stream()
                .sorted(Comparator.comparing(PaymentProfile::getId))
                .toList();
    }

    @Transactional
    public PlatformParameter updateParameter(String code, String value) {
        access.require("TREASURER");
        PlatformParameter parameter = store.find(PlatformParameter.class, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paramètre " + code));
        String next = value == null ? "" : value.trim();
        if (next.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Valeur vide");
        }
        if (code.endsWith("_TND") || code.endsWith("_EUR") || code.endsWith("_DAYS")) {
            BigDecimal amount = money(new BigDecimal(next));
            if (amount.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Valeur négative");
            }
            parameter.setValue(amount.toPlainString());
            alignFloors(code, amount);
        } else {
            parameter.setValue(next);
        }
        audit.record("PARAM_UPDATE", "PlatformParameter", code, parameter.getValue());
        return parameter;
    }

    @Transactional
    public PaymentProfile updateProfile(String id, String rule, String channelCode) {
        access.require("TREASURER");
        PaymentProfile profile = store.find(PaymentProfile.class, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil " + id));
        if (!"FIXED".equals(rule) && !"BY_AMOUNT".equals(rule)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Règle attendue : FIXED ou BY_AMOUNT");
        }
        if ("FIXED".equals(rule)) {
            channel(channelCode);
        }
        profile.setRule(rule);
        profile.setChannelCode("BY_AMOUNT".equals(rule) ? null : channelCode);
        audit.record("PROFILE_UPDATE", "PaymentProfile", id, rule + " " + profile.getChannelCode());
        return profile;
    }

    @Transactional(readOnly = true)
    public Route decide(RouteRequest request) {
        access.requireAny("TREASURER", "VALIDATOR", "COMPLIANCE");
        LegalEntity entity = store.find(LegalEntity.class, request.entityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entité " + request.entityId()));
        access.entity(entity.getId());
        PaymentPurpose purpose = PaymentPurpose.valueOf(request.purpose());
        return route(entity, request.currency(), money(request.amount()), purpose, request.beneficiaryEntityId());
    }

    @Transactional
    public Remittance remit(String entityId) {
        access.require("TREASURER");
        access.entity(entityId);
        List<IntegrationMessage> created = new ArrayList<>();
        int skipped = 0;
        for (PaymentOrder payment : store.query(
                "select p from PaymentOrder p where p.entityId = :entity and p.status = :status",
                PaymentOrder.class,
                Map.of("entity", entityId, "status", DealStatus.VALIDATED))) {
            if (alreadySent(payment.getId())) {
                skipped++;
                continue;
            }
            LegalEntity entity = store.find(LegalEntity.class, payment.getEntityId()).orElseThrow();
            Route decision = route(entity, payment.getCurrency(), payment.getAmount(), payment.getPurpose(), payment.getBeneficiaryEntityId());
            if (!decision.executable()) {
                payment.setStatusReason(decision.reason());
                skipped++;
                continue;
            }
            IntegrationMessage message = new IntegrationMessage();
            message.setId(Ids.next());
            message.setChannelCode(decision.channelCode());
            message.setDirection("OUT");
            message.setMessageFormat(decision.format());
            message.setEntityId(payment.getEntityId());
            message.setBusinessRef(payment.getId());
            message.setStatus("GENERATED");
            message.setPayload(envelope(payment, decision));
            message.setCreatedAt(Instant.now());
            message.setCreatedBy(access.current().id());
            store.insert(message);
            payment.setStatus(DealStatus.TRANSMITTED);
            payment.setStatusReason(decision.channelCode());
            created.add(message);
        }
        audit.record("REMIT", "LegalEntity", entityId, created.size() + " message(s), " + skipped + " ignoré(s)");
        return new Remittance(entityId, created.size(), skipped, created);
    }

    @Transactional(readOnly = true)
    public List<IntegrationMessage> messages(String entityId) {
        access.requireAny("TREASURER", "VALIDATOR", "COMPLIANCE", "AUDITOR");
        if (entityId == null || entityId.isBlank()) {
            return store.all(IntegrationMessage.class).stream()
                    .filter(message -> access.current().canSee(message.getEntityId()))
                    .sorted(Comparator.comparing(IntegrationMessage::getCreatedAt).reversed())
                    .toList();
        }
        access.entity(entityId);
        return store.query(
                "select m from IntegrationMessage m where m.entityId = :entity order by m.createdAt desc",
                IntegrationMessage.class,
                Map.of("entity", entityId));
    }

    @Transactional
    public ImportView ingestStatement(String batchId, String payload) {
        access.require("TREASURER");
        if (payload == null || !payload.contains(":25:")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Relevé MT940 attendu");
        }
        payload = payload.replace(" :", "\n:");
        String accountId = field(payload, ":25:");
        BankAccount account = store.find(BankAccount.class, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Compte inconnu " + accountId));
        access.entity(account.getEntityId());
        String closingLine = lineStarting(payload, ":62F:");
        if (closingLine == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Solde de clôture :62F: absent");
        }
        SwiftAmount closing = swiftAmount(closingLine.substring(5));
        List<MovementLine> movements = new ArrayList<>();
        for (String line : payload.split("\\R")) {
            if (!line.startsWith(":61:")) {
                continue;
            }
            SwiftEntry entry = swiftEntry(line.substring(4));
            movements.add(new MovementLine(
                    accountId + "-" + entry.reference(),
                    account.getEntityId(),
                    accountId,
                    closing.currency(),
                    entry.amount(),
                    entry.valueDate(),
                    entry.valueDate(),
                    entry.reference()));
        }
        BalanceLine balance = new BalanceLine(
                accountId + "-" + batchId,
                account.getEntityId(),
                accountId,
                closing.currency(),
                closing.amount(),
                closing.amount(),
                closing.date(),
                field(payload, ":20:"));
        return imports.ingest(new ImportRequest("MT940", batchId, List.of(balance), movements));
    }

    private void alignFloors(String code, BigDecimal amount) {
        if ("SGMT_FLOOR_TND".equals(code)) {
            ConnectivityChannel sgmt = channel("SGMT");
            ConnectivityChannel sibtel = channel("SIBTEL");
            sgmt.setMinAmount(amount);
            sibtel.setMaxAmount(money(amount.subtract(new BigDecimal("0.01"))));
        }
        if ("TARGET_FLOOR_EUR".equals(code)) {
            channel("TARGET").setMinAmount(amount);
        }
    }

    private Route route(LegalEntity entity, String currency, BigDecimal amount, PaymentPurpose purpose, String beneficiaryEntityId) {
        if ((purpose == PaymentPurpose.INTERCOMPANY || purpose == PaymentPurpose.TREASURY_TRANSFER)
                && beneficiaryEntityId != null
                && !beneficiaryEntityId.equals(entity.getId())
                && entity.isTransferRestricted()) {
            return new Route(null, null, false, entity.getTransferRestrictionReason());
        }
        PaymentProfile profile = profileFor(purpose, entity.getJurisdiction(), currency);
        if (profile != null && "FIXED".equals(profile.getRule())) {
            ConnectivityChannel selected = channel(profile.getChannelCode());
            return new Route(selected.getCode(), selected.getMessageFormat(), true, profile.getId());
        }
        if (entity.getJurisdiction() == Jurisdiction.TN && "TND".equals(currency)) {
            BigDecimal floor = decimal("SGMT_FLOOR_TND");
            return amount.compareTo(floor) >= 0 ? open("SGMT") : open("SIBTEL");
        }
        if (entity.getJurisdiction() == Jurisdiction.EU && "EUR".equals(currency)) {
            BigDecimal floor = decimal("TARGET_FLOOR_EUR");
            return amount.compareTo(floor) >= 0 ? open("TARGET") : open("SEPA");
        }
        return open("SWIFT");
    }

    private PaymentProfile profileFor(PaymentPurpose purpose, Jurisdiction jurisdiction, String currency) {
        return store.first(
                "select p from PaymentProfile p where p.purpose = :purpose and p.jurisdiction = :jurisdiction and p.currency = :currency",
                PaymentProfile.class,
                Map.of("purpose", purpose.name(), "jurisdiction", jurisdiction.name(), "currency", currency)).orElse(null);
    }

    private Route open(String code) {
        ConnectivityChannel selected = channel(code);
        return new Route(selected.getCode(), selected.getMessageFormat(), true,
                selected.getCode() + " " + selected.getCutoff() + " J+" + selected.getSettlementLagDays());
    }

    private ConnectivityChannel channel(String code) {
        return store.first("select c from ConnectivityChannel c where c.code = :code", ConnectivityChannel.class, Map.of("code", code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Canal " + code));
    }

    private BigDecimal decimal(String code) {
        return money(new BigDecimal(store.find(PlatformParameter.class, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Paramètre " + code))
                .getValue()));
    }

    private boolean alreadySent(String paymentId) {
        return store.first(
                "select m from IntegrationMessage m where m.businessRef = :ref",
                IntegrationMessage.class,
                Map.of("ref", paymentId)).isPresent();
    }

    private String envelope(PaymentOrder payment, Route decision) {
        return """
                NEXUS-ENVELOPE
                channel=%s
                format=%s
                debtor=%s
                account=%s
                beneficiary=%s
                amount=%s
                currency=%s
                purpose=%s
                valueDate=%s
                endToEnd=%s
                notice=sandbox-only
                """.formatted(
                decision.channelCode(),
                decision.format(),
                payment.getEntityId(),
                payment.getDebitAccountId(),
                payment.getBeneficiaryName(),
                payment.getAmount().toPlainString(),
                payment.getCurrency(),
                payment.getPurpose().name(),
                payment.getValueDate(),
                payment.getId()).trim();
    }

    private String field(String payload, String tag) {
        String line = lineStarting(payload, tag);
        if (line == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Balise absente " + tag);
        }
        return line.substring(tag.length()).trim();
    }

    private String lineStarting(String payload, String tag) {
        for (String line : payload.split("\\R")) {
            if (line.startsWith(tag)) {
                return line;
            }
        }
        return null;
    }

    private SwiftAmount swiftAmount(String body) {
        LocalDate date = LocalDate.of(2000 + Integer.parseInt(body.substring(1, 3)),
                Integer.parseInt(body.substring(3, 5)), Integer.parseInt(body.substring(5, 7)));
        String currency = body.substring(7, 10);
        BigDecimal amount = money(new BigDecimal(body.substring(10).trim().replace(',', '.')));
        if (body.charAt(0) == 'D') {
            amount = amount.negate();
        }
        return new SwiftAmount(date, currency, amount);
    }

    private SwiftEntry swiftEntry(String body) {
        LocalDate date = LocalDate.of(2000 + Integer.parseInt(body.substring(0, 2)),
                Integer.parseInt(body.substring(2, 4)), Integer.parseInt(body.substring(4, 6)));
        int sign = body.charAt(6) == 'D' ? -1 : 1;
        int marker = body.indexOf('N');
        BigDecimal amount = money(new BigDecimal(body.substring(7, marker).replace(',', '.'))).multiply(BigDecimal.valueOf(sign));
        String reference = body.substring(marker + 4).trim();
        return new SwiftEntry(date, money(amount), reference);
    }

    public record Route(String channelCode, String format, boolean executable, String reason) {}

    public record Remittance(String entityId, int generated, int skipped, List<IntegrationMessage> messages) {}

    private record SwiftAmount(LocalDate date, String currency, BigDecimal amount) {}

    private record SwiftEntry(LocalDate valueDate, BigDecimal amount, String reference) {}
}
