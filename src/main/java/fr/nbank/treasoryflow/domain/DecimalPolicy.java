package fr.nbank.treasoryflow.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalPolicy {
    public static final int AMOUNT_SCALE = 2;
    public static final int RATIO_SCALE = 6;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private DecimalPolicy() {}

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, ROUNDING);
    }

    public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            throw new IllegalArgumentException("Dénominateur nul : le ratio n'est pas défini");
        }
        return numerator.divide(denominator, RATIO_SCALE, ROUNDING);
    }

    public static boolean sameMoney(BigDecimal left, BigDecimal right) {
        return money(left).compareTo(money(right)) == 0;
    }
}
