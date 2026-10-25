package com.taxdoc.planning.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Rev. Proc. 2025-32 (section 4.01, tables 1-3) prints the tax owed at every bracket boundary
 * ("$5,800 plus 22% of the excess over $50,400"). Those amounts are an independent check: if
 * any threshold or rate in tax-years.yml were mistyped, the tax we compute at the boundaries
 * would no longer match the IRS's numbers.
 */
class PublishedBracketsTest {

    private final TaxYearRules y2026 = TaxYearsYaml.load().forYear(2026);

    @ParameterizedTest(name = "{0} at ${1} owes ${2}")
    @CsvSource({
        "SINGLE,                  50400,  5800",
        "SINGLE,                 105700,  17966",
        "SINGLE,                 201775,  41024",
        "SINGLE,                 256225,  58448",
        "SINGLE,                 640600,  192979.25",
        "MARRIED_FILING_JOINTLY, 100800,  11600",
        "MARRIED_FILING_JOINTLY, 211400,  35932",
        "MARRIED_FILING_JOINTLY, 403550,  82048",
        "MARRIED_FILING_JOINTLY, 512450,  116896",
        "MARRIED_FILING_JOINTLY, 768700,  206583.50",
        "HEAD_OF_HOUSEHOLD,       67450,  7740",
        "HEAD_OF_HOUSEHOLD,      105700,  16155",
        "HEAD_OF_HOUSEHOLD,      201750,  39207",
        "HEAD_OF_HOUSEHOLD,      256200,  56631",
        "HEAD_OF_HOUSEHOLD,      640600,  191171",
    })
    void matchesTaxPublishedAtEachBoundary(FilingStatus status, BigDecimal taxableIncome, BigDecimal published) {
        assertThat(y2026.incomeTax(status, taxableIncome)).isEqualByComparingTo(published);
    }

    @Test
    void midBracketHandCheck() {
        // Single, $100,000 taxable: 5,800 + 22% x (100,000 - 50,400) = 5,800 + 10,912 = 16,712
        assertThat(y2026.incomeTax(FilingStatus.SINGLE, new BigDecimal("100000"))).isEqualByComparingTo("16712.00");
    }

    @Test
    void zeroIncomeOwesNothing() {
        assertThat(y2026.incomeTax(FilingStatus.SINGLE, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "{0} at ${1}: marginal {2}")
    @CsvSource({
        "SINGLE,                      0, 0.10",
        "SINGLE,                  50399, 0.12",
        "SINGLE,                  50400, 0.22", // the next dollar is "over $50,400"
        "SINGLE,                 700000, 0.37",
        "MARRIED_FILING_JOINTLY, 212000, 0.24",
    })
    void marginalRate(FilingStatus status, BigDecimal taxableIncome, BigDecimal expected) {
        assertThat(y2026.marginalRate(status, taxableIncome)).isEqualByComparingTo(expected);
    }
}
