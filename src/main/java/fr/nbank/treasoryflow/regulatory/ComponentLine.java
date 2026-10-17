package fr.nbank.treasoryflow.regulatory;

import java.math.BigDecimal;

public record ComponentLine(String component, String side, BigDecimal amount, BigDecimal weight,
        BigDecimal contribution, String explanation) {}
