package com.taxdoc.planning.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Formats amounts for explanations. Estimates are approximate, so text shows whole dollars. */
final class Dollars {

    private Dollars() {}

    /** 26139.67 -> "$26,140". */
    static String format(BigDecimal amount) {
        return String.format(Locale.US, "$%,d", amount.setScale(0, RoundingMode.HALF_UP).toBigIntegerExact());
    }

    /** 0.60 -> "60%". */
    static String percent(BigDecimal fraction) {
        return fraction.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
