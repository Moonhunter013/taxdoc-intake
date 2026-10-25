package com.taxdoc.planning.rules;

import java.math.BigDecimal;

/** One row of a rate table: {@code rate} applies to taxable income above {@code over}. */
public record TaxBracket(BigDecimal over, BigDecimal rate) {

    public TaxBracket {
        Checks.present("bracket.over", over);
        if (over.signum() < 0) {
            throw new IllegalArgumentException("bracket.over must not be negative, was " + over);
        }
        Checks.rate("bracket.rate", rate);
    }
}
