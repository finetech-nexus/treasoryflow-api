package fr.nbank.treasoryflow.regulatory;

import static fr.nbank.treasoryflow.domain.DecimalPolicy.ROUNDING;
import static fr.nbank.treasoryflow.domain.DecimalPolicy.money;
import static fr.nbank.treasoryflow.domain.DecimalPolicy.ratio;

import fr.nbank.treasoryflow.domain.Codes.FormulaCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Regulatory arithmetic. Weights, haircuts and caps come from the published rule version.
 * Screens never embed these formulas.
 */
public final class LiquidityCalculator {
    private LiquidityCalculator() {}

    public static Outcome calculate(FormulaCode formula, Map<String, BigDecimal> parameters, List<Item> items) {
        return switch (formula) {
            case TN_LIQUIDITY_STOCK -> stockRatio(formula, "ASSET", "LIABILITY", parameters, items);
            case EU_NSFR -> stockRatio(formula, "ASF", "RSF", parameters, items);
            case EU_LCR -> lcr(parameters, items);
        };
    }

    private static Outcome stockRatio(FormulaCode formula, String numeratorSide, String denominatorSide,
            Map<String, BigDecimal> parameters, List<Item> items) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        List<ComponentLine> lines = new ArrayList<>();
        String factorCode = formula == FormulaCode.EU_NSFR ? "FACTOR" : "WEIGHT";
        for (Item item : items) {
            if (!numeratorSide.equals(item.side()) && !denominatorSide.equals(item.side())) {
                throw new IllegalArgumentException("Sens " + item.side() + " incompatible avec " + formula);
            }
            BigDecimal factor = required(parameters, factorCode, item.bucket());
            BigDecimal contribution = money(item.amount().multiply(factor));
            lines.add(new ComponentLine(item.bucket(), item.side(), money(item.amount()), factor, contribution,
                    item.sourceReference()));
            if (numeratorSide.equals(item.side())) {
                numerator = numerator.add(contribution);
            } else {
                denominator = denominator.add(contribution);
            }
        }
        numerator = money(numerator);
        denominator = money(denominator);
        BigDecimal result = ratio(numerator, denominator);
        lines.add(new ComponentLine("NUMERATOR", numeratorSide, numerator, BigDecimal.ONE, numerator, ""));
        lines.add(new ComponentLine("DENOMINATOR", denominatorSide, denominator, BigDecimal.ONE, denominator, ""));
        return new Outcome(result, Map.of(numeratorSide, numerator, denominatorSide, denominator), lines);
    }

    private static Outcome lcr(Map<String, BigDecimal> parameters, List<Item> items) {
        BigDecimal l1Gross = BigDecimal.ZERO;
        BigDecimal l2aGross = BigDecimal.ZERO;
        BigDecimal l2bGross = BigDecimal.ZERO;
        BigDecimal outflows = BigDecimal.ZERO;
        BigDecimal inflows = BigDecimal.ZERO;
        List<ComponentLine> lines = new ArrayList<>();
        for (Item item : items) {
            switch (item.side()) {
                case "HQLA" -> {
                    BigDecimal haircut = required(parameters, "HAIRCUT", item.bucket());
                    BigDecimal kept = money(item.amount().multiply(BigDecimal.ONE.subtract(haircut)));
                    lines.add(new ComponentLine(item.bucket(), "HQLA", money(item.amount()), haircut, kept,
                            item.sourceReference()));
                    switch (item.bucket()) {
                        case "L1" -> l1Gross = l1Gross.add(kept);
                        case "L2A" -> l2aGross = l2aGross.add(kept);
                        case "L2B" -> l2bGross = l2bGross.add(kept);
                        default -> throw new IllegalArgumentException("Catégorie HQLA inconnue : " + item.bucket());
                    }
                }
                case "OUTFLOW", "INFLOW" -> {
                    BigDecimal weight = required(parameters, "WEIGHT", item.bucket());
                    BigDecimal weighted = money(item.amount().multiply(weight));
                    lines.add(new ComponentLine(item.bucket(), item.side(), money(item.amount()), weight, weighted,
                            item.sourceReference()));
                    if ("OUTFLOW".equals(item.side())) {
                        outflows = outflows.add(weighted);
                    } else {
                        inflows = inflows.add(weighted);
                    }
                }
                default -> throw new IllegalArgumentException("Sens " + item.side() + " incompatible avec EU_LCR");
            }
        }
        BigDecimal l1 = money(l1Gross);
        BigDecimal l2a = money(l2aGross);
        BigDecimal l2b = money(l2bGross);
        BigDecimal l2bCeiling = portion(l1.add(l2a), required(parameters, "CAP_NUM", "L2B"), required(parameters, "CAP_DEN", "L2B"));
        BigDecimal l2bAdj = l2b.min(l2bCeiling);
        BigDecimal l2Ceiling = portion(l1, required(parameters, "CAP_NUM", "L2"), required(parameters, "CAP_DEN", "L2"));
        BigDecimal l2aAdj = l2a;
        BigDecimal combined = l2aAdj.add(l2bAdj);
        if (combined.compareTo(l2Ceiling) > 0) {
            BigDecimal excess = combined.subtract(l2Ceiling);
            BigDecimal fromA = l2aAdj.min(excess);
            l2aAdj = l2aAdj.subtract(fromA);
            l2bAdj = l2bAdj.subtract(excess.subtract(fromA));
            lines.add(new ComponentLine("L2_CAP", "CAP", excess, l2Ceiling, money(l2aAdj.add(l2bAdj)),
                    "Plafond des actifs de niveau 2"));
        }
        if (l2b.compareTo(l2bAdj) != 0) {
            lines.add(new ComponentLine("L2B_CAP", "CAP", l2b.subtract(l2bAdj), l2bCeiling, l2bAdj,
                    "Plafond des actifs de niveau 2B"));
        }
        BigDecimal hqla = money(l1.add(l2aAdj).add(l2bAdj));
        outflows = money(outflows);
        inflows = money(inflows);
        BigDecimal inflowCap = required(parameters, "INFLOW_CAP", "*");
        BigDecimal inflowCeiling = money(outflows.multiply(inflowCap));
        if (inflows.compareTo(inflowCeiling) > 0 && inflowCeiling.compareTo(outflows) > 0) {
            throw new IllegalArgumentException("Le plafond d'entrées dépasse les sorties");
        }
        BigDecimal cappedInflows = inflows.min(inflowCeiling);
        if (cappedInflows.compareTo(outflows) > 0) {
            throw new IllegalArgumentException("Le plafond d'entrées dépasse les sorties");
        }
        lines.add(new ComponentLine("INFLOWS_CAPPED", "INFLOW", inflows, inflowCap, cappedInflows, ""));
        BigDecimal netOutflows = money(outflows.subtract(cappedInflows));
        BigDecimal result = ratio(hqla, netOutflows);
        lines.add(new ComponentLine("HQLA", "HQLA", hqla, BigDecimal.ONE, hqla, ""));
        lines.add(new ComponentLine("NET_OUTFLOWS", "OUTFLOW", netOutflows, BigDecimal.ONE, netOutflows, ""));
        return new Outcome(result, Map.of("HQLA", hqla, "NET_OUTFLOWS", netOutflows, "OUTFLOWS", outflows, "INFLOWS", cappedInflows), lines);
    }

    private static BigDecimal portion(BigDecimal base, BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            throw new IllegalArgumentException("Dénominateur de plafond nul");
        }
        return base.multiply(numerator).divide(denominator, 2, ROUNDING);
    }

    private static BigDecimal required(Map<String, BigDecimal> parameters, String code, String component) {
        BigDecimal value = parameters.get(code + "|" + component);
        if (value == null) {
            throw new IllegalArgumentException("Paramètre manquant : " + code + " / " + component);
        }
        return value;
    }
}
