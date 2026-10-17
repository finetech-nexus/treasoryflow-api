package fr.nbank.treasoryflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ApiTypes {
    private ApiTypes() {}

    public record DealRequest(
            @NotBlank String entityId,
            @NotBlank String productCode,
            @NotBlank String currency,
            String counterCurrency,
            @NotNull BigDecimal amount,
            BigDecimal rate,
            @NotBlank String counterpartyId,
            @NotBlank String reference,
            @NotNull LocalDate tradeDate,
            @NotNull LocalDate valueDate,
            LocalDate maturityDate) {}

    public record BalanceLine(
            @NotBlank String externalId,
            @NotBlank String entityId,
            @NotBlank String accountId,
            @NotBlank String currency,
            @NotNull BigDecimal bookedAmount,
            BigDecimal availableAmount,
            @NotNull LocalDate asOf,
            @NotBlank String sourceReference) {}

    public record MovementLine(
            @NotBlank String externalId,
            @NotBlank String entityId,
            @NotBlank String accountId,
            @NotBlank String currency,
            @NotNull BigDecimal amount,
            @NotNull LocalDate valueDate,
            @NotNull LocalDate bookingDate,
            @NotBlank String reference) {}

    public record ImportRequest(
            @NotBlank String sourceSystem,
            @NotBlank String externalBatchId,
            List<BalanceLine> balances,
            List<MovementLine> movements) {}

    public record ImportView(String id, String status, int accepted, int rejected, boolean replayed, String payloadHash) {}

    public record ParameterLine(@NotBlank String code, @NotBlank String component, @NotNull BigDecimal value, @NotBlank String unit) {}

    public record RuleDraft(
            @NotBlank String ruleId,
            @NotBlank String jurisdiction,
            @NotBlank String entityScope,
            @NotBlank String regulatoryReference,
            @NotBlank String ruleVersion,
            @NotNull LocalDate validFrom,
            @NotBlank String formulaCode,
            @NotBlank String testReference,
            @NotNull List<ParameterLine> parameters) {}

    public record CalculationRequest(@NotBlank String entityId, @NotBlank String formulaCode, @NotNull LocalDate referenceDate) {}

    public record ItemRequest(
            @NotBlank String entityId,
            @NotNull LocalDate asOf,
            @NotBlank String formulaCode,
            @NotBlank String side,
            @NotBlank String bucket,
            @NotBlank String currency,
            @NotNull BigDecimal amount,
            @NotBlank String sourceReference) {}

    public record FlowRequest(
            @NotBlank String entityId,
            @NotBlank String accountId,
            @NotBlank String currency,
            @NotNull LocalDate valueDate,
            @NotNull BigDecimal amount,
            @NotBlank String flowClass,
            @NotBlank String scenarioCode,
            @NotBlank String label,
            boolean cancellable) {}

    public record ProjectRequest(@NotBlank String entityId, @NotBlank String scenario, @NotNull LocalDate asOf, int horizonDays) {}

    public record ValuationLine(
            String entityId,
            String accountId,
            String originalCurrency,
            BigDecimal originalAmount,
            BigDecimal rate,
            String rateSource,
            LocalDate rateDate,
            String method,
            String reportingCurrency,
            BigDecimal reportingAmount,
            boolean intercompany,
            String pairId,
            boolean freelyTransferable,
            String transferReason) {}

    public record GroupView(
            String snapshotId,
            String scopeId,
            LocalDate asOf,
            String reportingCurrency,
            BigDecimal consolidatedAmount,
            BigDecimal eliminatedAmount,
            BigDecimal freelyTransferable,
            BigDecimal restricted,
            List<String> eliminatedPairIds,
            List<ValuationLine> lines) {}

    public record ProjectionPoint(LocalDate date, String currency, BigDecimal projected, String nature) {}

    public record Projection(String entityId, String scenario, LocalDate asOf, String nature, List<ProjectionPoint> points) {}

    public record GapBucket(String bucket, String currency, BigDecimal asset, BigDecimal liability, BigDecimal gap) {}

    public record ReplayView(String runId, BigDecimal storedResult, BigDecimal replayedResult, boolean matched, String inputSnapshotHash) {}

    public record PaymentRequest(
            @NotBlank String entityId,
            @NotBlank String debitAccountId,
            @NotBlank String beneficiaryName,
            String beneficiaryEntityId,
            @NotBlank String currency,
            @NotNull BigDecimal amount,
            @NotNull LocalDate valueDate,
            @NotBlank String purpose) {}
}
