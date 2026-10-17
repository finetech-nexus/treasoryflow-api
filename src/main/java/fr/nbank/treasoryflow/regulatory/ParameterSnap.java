package fr.nbank.treasoryflow.regulatory;

import java.math.BigDecimal;

public record ParameterSnap(String code, String component, BigDecimal value) {}
