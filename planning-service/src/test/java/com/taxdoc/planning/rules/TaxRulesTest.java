package com.taxdoc.planning.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taxdoc.planning.api.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class TaxRulesTest {

    private final TaxRules rules = TaxYearsYaml.load();

    @Test
    void listsConfiguredYears() {
        assertThat(rules.supportedYears()).containsExactly(2026);
    }

    @Test
    void unconfiguredYearIsAnErrorNotAFallback() {
        assertThatThrownBy(() -> rules.forYear(2027))
                .isInstanceOf(UnsupportedTaxYearException.class)
                .hasMessage("Tax year 2027 is not configured. Supported years: [2026]");
    }

    @Test
    void unconfiguredYearMapsTo422WithSupportedYears() {
        UnsupportedTaxYearException e = catchUnsupported(2025);

        ProblemDetail problem = new ApiExceptionHandler().unsupportedTaxYear(e);

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Unsupported tax year");
        assertThat(problem.getProperties())
                .containsEntry("taxYear", 2025)
                .containsKey("supportedYears");
    }

    private UnsupportedTaxYearException catchUnsupported(int year) {
        try {
            rules.forYear(year);
        } catch (UnsupportedTaxYearException e) {
            return e;
        }
        throw new AssertionError("expected UnsupportedTaxYearException for " + year);
    }
}
