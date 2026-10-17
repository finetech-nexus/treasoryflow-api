package fr.nbank.treasoryflow.regulatory;

import java.math.BigDecimal;

public record ItemSnap(String side, String bucket, BigDecimal amount, String sourceReference) {}
