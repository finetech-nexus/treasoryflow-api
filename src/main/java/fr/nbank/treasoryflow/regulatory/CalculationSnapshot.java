package fr.nbank.treasoryflow.regulatory;

import java.math.BigDecimal;
import java.util.List;

public record CalculationSnapshot(
        String formulaCode,
        String ruleVersionId,
        String ruleVersion,
        List<ParameterSnap> parameters,
        List<ItemSnap> items) {}
