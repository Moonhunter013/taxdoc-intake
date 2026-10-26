package com.taxdoc.planning.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.taxdoc.planning.rules.FilingStatus;
import com.taxdoc.planning.rules.TaxYearRules;
import com.taxdoc.planning.rules.TaxYearsYaml;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Schedule SE and payroll edge cases, hand-checked with the 2026 values (wage base 184,500). */
class PayrollTaxesTest {

    private final TaxYearRules y2026 = TaxYearsYaml.load().forYear(2026);

    @ParameterizedTest(name = "profit {0}, W-2 SS wages {1} -> SE tax {2}")
    @CsvSource({
        // NE 433 x 0.9235 = 399.88, under the $400 minimum (Schedule SE line 4c): no tax.
        "433, 0, 0.00",
        // NE 434 x 0.9235 = 400.80 x 15.3% = 61.32: just over the minimum.
        "434, 0, 61.32",
        // NE 250,000 x 0.9235 = 230,875: 12.4% x 184,500 = 22,878 + 2.9% x 230,875 = 6,695.38.
        "250000, 0, 29573.38",
        // W-2 wages used 150,000 of the base; 34,500 left: 12.4% x 34,500 = 4,278 + 2.9% x 92,350 = 2,678.15.
        "100000, 150000, 6956.15",
        // W-2 wages above the base: Medicare only, 2.9% x 92,350 = 2,678.15 (line 9 is zero).
        "100000, 200000, 2678.15",
        // A loss means no SE tax.
        "-5000, 0, 0.00",
    })
    void selfEmploymentTaxFollowsScheduleSE(String profit, String w2Wages, String expected) {
        BigDecimal tax = PayrollTaxes.selfEmploymentTax(y2026, new BigDecimal(profit), new BigDecimal(w2Wages));

        assertThat(tax.setScale(2, RoundingMode.HALF_UP)).isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "profit {0}, Medicare wages {1} -> Additional Medicare {2}")
    @CsvSource({
        // NE 184,700 is under the 200,000 threshold.
        "200000, 0, 0.00",
        // NE 286,285 - 200,000 = 86,285 x 0.9% = 776.57 (client-09).
        "310000, 0, 776.57",
        // Wages 150,000 leave 50,000 of threshold; NE 92,350 - 50,000 = 42,350 x 0.9% = 381.15.
        "100000, 150000, 381.15",
    })
    void additionalMedicareThresholdIsReducedByWages(String profit, String medicareWages, String expected) {
        BigDecimal tax = PayrollTaxes.additionalMedicareOnSelfEmployment(
                y2026, FilingStatus.SINGLE, new BigDecimal(profit), new BigDecimal(medicareWages));

        assertThat(tax.setScale(2, RoundingMode.HALF_UP)).isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "salary {0} with W-2 SS {1} / Medicare {2} -> payroll tax {3}")
    @CsvSource({
        // No other wages: 15.3% x 100,000.
        "100000, 0, 0, 15300.00",
        // Salary above the wage base: 12.4% x 184,500 = 22,878 + 2.9% x 190,000 = 5,510.
        "190000, 0, 0, 28388.00",
        // W-2 already at the base: employer 6.2% x 21,000 = 1,302 (never refunded), employee 0,
        // Medicare 609, Additional Medicare on the whole salary (wages already above 200,000) 189.
        "21000, 184500, 235700, 2100.00",
        // W-2 wages 180,000: employer 6.2% x 50,000 = 3,100; employee 6.2% x 4,500 (base left) = 279;
        // Medicare 2.9% x 50,000 = 1,450; Medicare wages go 180,000 -> 230,000, so 0.9% x 30,000 = 270.
        "50000, 180000, 180000, 5099.00",
    })
    void payrollTaxOnSalary(String salary, String w2SsWages, String medicareWages, String expected) {
        BigDecimal tax = PayrollTaxes.payrollTaxOnSalary(y2026, FilingStatus.SINGLE, new BigDecimal(salary),
                new BigDecimal(w2SsWages), new BigDecimal(medicareWages));

        assertThat(tax.setScale(2, RoundingMode.HALF_UP)).isEqualTo(new BigDecimal(expected));
    }
}
