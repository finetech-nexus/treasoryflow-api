package fr.nbank.treasoryflow;

import static org.assertj.core.api.Assertions.assertThat;

import fr.nbank.treasoryflow.domain.Codes.FormulaCode;
import fr.nbank.treasoryflow.regulatory.Item;
import fr.nbank.treasoryflow.regulatory.LiquidityCalculator;
import fr.nbank.treasoryflow.regulatory.Outcome;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiquidityCalculatorTest {
    @Test
    void tunisianRatioMatchesThePinnedBaseline() {
        Outcome outcome = LiquidityCalculator.calculate(FormulaCode.TN_LIQUIDITY_STOCK, Map.of(
                "WEIGHT|CASH_BCT", new BigDecimal("1"),
                "WEIGHT|SOVEREIGN_TN", new BigDecimal("1"),
                "WEIGHT|IB_PLACEMENT_LE_1M", new BigDecimal("1"),
                "WEIGHT|DEMAND_DEPOSITS", new BigDecimal("1"),
                "WEIGHT|TERM_DEPOSITS_LE_1M", new BigDecimal("1")), List.of(
                item("ASSET", "CASH_BCT", "500000000"),
                item("ASSET", "SOVEREIGN_TN", "200000000"),
                item("ASSET", "IB_PLACEMENT_LE_1M", "100000000"),
                item("LIABILITY", "DEMAND_DEPOSITS", "600000000"),
                item("LIABILITY", "TERM_DEPOSITS_LE_1M", "150000000")));
        assertThat(outcome.result()).isEqualByComparingTo("1.066667");
        assertThat(outcome.totals().get("ASSET")).isEqualByComparingTo("800000000.00");
        assertThat(outcome.totals().get("LIABILITY")).isEqualByComparingTo("750000000.00");
    }

    @Test
    void europeanLcrMatchesThePinnedBaseline() {
        Outcome outcome = LiquidityCalculator.calculate(FormulaCode.EU_LCR, lcrParameters(), List.of(
                item("HQLA", "L1", "1000000000"),
                item("HQLA", "L2A", "200000000"),
                item("HQLA", "L2B", "100000000"),
                item("OUTFLOW", "RETAIL_STABLE", "400000000"),
                item("OUTFLOW", "WHOLESALE_FINANCIAL", "300000000"),
                item("INFLOW", "INFLOW_FINANCIAL", "100000000")));
        assertThat(outcome.result()).isEqualByComparingTo("5.545455");
        assertThat(outcome.totals().get("HQLA")).isEqualByComparingTo("1220000000.00");
        assertThat(outcome.totals().get("NET_OUTFLOWS")).isEqualByComparingTo("220000000.00");
    }

    @Test
    void level2CapsBindBeforeTheRatio() {
        Outcome outcome = LiquidityCalculator.calculate(FormulaCode.EU_LCR, lcrParameters(), List.of(
                item("HQLA", "L1", "100"),
                item("HQLA", "L2A", "100"),
                item("HQLA", "L2B", "100"),
                item("OUTFLOW", "WHOLESALE_FINANCIAL", "50")));
        assertThat(outcome.totals().get("HQLA")).isEqualByComparingTo("166.67");
    }

    @Test
    void inflowsCannotExceedTheConfiguredCap() {
        Map<String, BigDecimal> parameters = new java.util.LinkedHashMap<>(lcrParameters());
        Outcome outcome = LiquidityCalculator.calculate(FormulaCode.EU_LCR, parameters, List.of(
                item("HQLA", "L1", "100"),
                item("OUTFLOW", "WHOLESALE_FINANCIAL", "100"),
                item("INFLOW", "INFLOW_FINANCIAL", "90")));
        assertThat(outcome.totals().get("INFLOWS")).isEqualByComparingTo("75.00");
        assertThat(outcome.totals().get("NET_OUTFLOWS")).isEqualByComparingTo("25.00");
        assertThat(outcome.result()).isEqualByComparingTo("4.000000");
    }

    @Test
    void nsfrMatchesThePinnedBaseline() {
        Outcome outcome = LiquidityCalculator.calculate(FormulaCode.EU_NSFR, Map.of(
                "FACTOR|CAPITAL_GT_1Y", new BigDecimal("1"),
                "FACTOR|RETAIL_STABLE", new BigDecimal("0.95"),
                "FACTOR|HQLA_L1", new BigDecimal("0"),
                "FACTOR|LOAN_NF_GT_1Y", new BigDecimal("0.85"),
                "FACTOR|OTHER_ASSETS", new BigDecimal("1")), List.of(
                item("ASF", "CAPITAL_GT_1Y", "500000000"),
                item("ASF", "RETAIL_STABLE", "400000000"),
                item("RSF", "HQLA_L1", "1000000000"),
                item("RSF", "LOAN_NF_GT_1Y", "600000000"),
                item("RSF", "OTHER_ASSETS", "100000000")));
        assertThat(outcome.result()).isEqualByComparingTo("1.442623");
        assertThat(outcome.totals().get("ASF")).isEqualByComparingTo("880000000.00");
        assertThat(outcome.totals().get("RSF")).isEqualByComparingTo("610000000.00");
    }

    @Test
    void aMissingWeightRejectsTheRun() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                LiquidityCalculator.calculate(FormulaCode.TN_LIQUIDITY_STOCK, Map.of(), List.of(item("ASSET", "CASH_BCT", "1"))));
    }

    private static Map<String, BigDecimal> lcrParameters() {
        Map<String, BigDecimal> parameters = new java.util.LinkedHashMap<>();
        parameters.put("HAIRCUT|L1", new BigDecimal("0"));
        parameters.put("HAIRCUT|L2A", new BigDecimal("0.15"));
        parameters.put("HAIRCUT|L2B", new BigDecimal("0.50"));
        parameters.put("WEIGHT|RETAIL_STABLE", new BigDecimal("0.05"));
        parameters.put("WEIGHT|WHOLESALE_FINANCIAL", new BigDecimal("1"));
        parameters.put("WEIGHT|INFLOW_FINANCIAL", new BigDecimal("1"));
        parameters.put("CAP_NUM|L2B", new BigDecimal("15"));
        parameters.put("CAP_DEN|L2B", new BigDecimal("85"));
        parameters.put("CAP_NUM|L2", new BigDecimal("2"));
        parameters.put("CAP_DEN|L2", new BigDecimal("3"));
        parameters.put("INFLOW_CAP|*", new BigDecimal("0.75"));
        return parameters;
    }

    private static Item item(String side, String bucket, String amount) {
        return new Item(side, bucket, new BigDecimal(amount), bucket);
    }
}
