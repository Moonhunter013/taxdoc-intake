package com.taxdoc.planning.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;

/** Loading and validation of tax-years.yml. */
class TaxYearConfigTest {

    private final TaxYearRules y2026 = TaxYearsYaml.load().forYear(2026);

    @Test
    void bindsTheVerified2026Values() {
        // Spot checks against the sources linked in tax-years.yml, one per section, so a typo
        // or a binding mix-up (e.g. kebab-case names not mapping) is caught.
        assertThat(y2026.socialSecurity().wageBase()).isEqualByComparingTo("184500");
        assertThat(y2026.socialSecurity().employeeRate()).isEqualByComparingTo("0.062");
        assertThat(y2026.socialSecurity().additionalMedicareThreshold().get(FilingStatus.MARRIED_FILING_JOINTLY))
                .isEqualByComparingTo("250000");
        assertThat(y2026.selfEmployment().netEarningsFactor()).isEqualByComparingTo("0.9235");
        assertThat(y2026.selfEmployment().minimumNetEarnings()).isEqualByComparingTo("400");
        assertThat(y2026.retirement().electiveDeferralLimit()).isEqualByComparingTo("24500");
        assertThat(y2026.retirement().annualAdditionsLimit()).isEqualByComparingTo("72000");
        assertThat(y2026.hsa().familyLimit()).isEqualByComparingTo("8750");
        assertThat(y2026.standardDeduction(FilingStatus.SINGLE)).isEqualByComparingTo("16100");
        assertThat(y2026.standardDeduction(FilingStatus.HEAD_OF_HOUSEHOLD)).isEqualByComparingTo("24150");
        assertThat(y2026.brackets().get(FilingStatus.SINGLE)).hasSize(7);
    }

    @Test
    void maxSocialSecurityTaxIsWageBaseTimesRate() {
        // 184,500 x 6.2% = 11,439.00. Used for the excess-withholding strategy (client-10).
        assertThat(y2026.socialSecurity().maxEmployeeTax()).isEqualByComparingTo("11439.00");
    }

    @Test
    void catchUpDependsOnAge() {
        TaxYearRules.Retirement r = y2026.retirement();
        assertThat(r.catchUp(49)).isEqualByComparingTo("0");
        assertThat(r.catchUp(50)).isEqualByComparingTo("8000");
        assertThat(r.catchUp(59)).isEqualByComparingTo("8000");
        assertThat(r.catchUp(60)).isEqualByComparingTo("11250");
        assertThat(r.catchUp(63)).isEqualByComparingTo("11250");
        assertThat(r.catchUp(64)).isEqualByComparingTo("8000"); // back to the regular amount
    }

    @Test
    void missingValueFailsWithItsPropertyName() {
        Map<String, String> props = TaxYearsYaml.properties();
        TaxYearsYaml.remove(props, "retirement.ira-limit");

        assertThatThrownBy(() -> TaxYearsYaml.bind(props))
                .isInstanceOf(BindException.class)
                .rootCause()
                .hasMessage("retirement.ira-limit is missing");
    }

    @Test
    void outOfRangeRateIsRejected() {
        Map<String, String> props = TaxYearsYaml.properties();
        TaxYearsYaml.set(props, "social-security.employee-rate", "6.2"); // percent, not fraction

        assertThatThrownBy(() -> TaxYearsYaml.bind(props))
                .rootCause()
                .hasMessageContaining("social-security.employee-rate must be a fraction between 0 and 1");
    }

    @Test
    void bracketsOutOfOrderAreRejected() {
        Map<String, String> props = TaxYearsYaml.properties();
        TaxYearsYaml.set(props, "brackets.single[2].over", "10000"); // below the previous 12,400

        assertThatThrownBy(() -> TaxYearsYaml.bind(props))
                .rootCause()
                .hasMessageContaining("brackets.single must be in increasing order; check entry 3");
    }

    @Test
    void noYearsConfiguredFailsWithAHint() {
        assertThatThrownBy(() -> new TaxRulesProperties(Map.of()))
                .hasMessageContaining("is tax-years.yml imported");
    }

    @Test
    void ratesAreExactDecimals() {
        // BigDecimal from YAML, not double: 0.062 must not become 0.06200000000000000122...
        assertThat(y2026.socialSecurity().employeeRate()).isEqualTo(new BigDecimal("0.062"));
    }
}
