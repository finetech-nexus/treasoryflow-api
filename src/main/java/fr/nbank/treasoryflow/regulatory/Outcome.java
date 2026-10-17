package fr.nbank.treasoryflow.regulatory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record Outcome(BigDecimal result, Map<String, BigDecimal> totals, List<ComponentLine> lines) {}
