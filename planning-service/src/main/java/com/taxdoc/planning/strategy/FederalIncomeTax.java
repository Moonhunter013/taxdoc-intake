package com.taxdoc.planning.strategy;

import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.rules.FilingStatus;
import com.taxdoc.planning.rules.TaxYearRules;
import java.math.BigDecimal;

/**
 * A simplified federal income tax picture, enough to estimate what a deduction saves. Taxable
 * income = W-2 box 1 + 1099-NEC income - half of SE tax - standard deduction. Not modelled (and
 * listed in results): the QBI deduction, credits, other income, itemised deductions.
 */
final class FederalIncomeTax {

    /** Deduction for one-half of self-employment tax: Schedule SE line 13, multiply by 50%. */
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private FederalIncomeTax() {}

    static BigDecimal halfSelfEmploymentTax(TaxProfile profile, TaxYearRules rules) {
        return PayrollTaxes.selfEmploymentTax(rules, profile.nonemployeeCompensation(), profile.w2SocialSecurityWages())
                .multiply(HALF);
    }

    static BigDecimal taxableIncome(TaxProfile profile, TaxYearRules rules, FilingStatus status) {
        return profile.w2Wages()
                .add(profile.nonemployeeCompensation())
                .subtract(halfSelfEmploymentTax(profile, rules))
                .subtract(rules.standardDeduction(status))
                .max(BigDecimal.ZERO);
    }

    /**
     * Income tax saved by a pre-tax contribution of {@code amount}: tax before minus tax after,
     * so a contribution spanning two brackets is priced correctly.
     */
    static BigDecimal taxSaved(TaxProfile profile, TaxYearRules rules, FilingStatus status, BigDecimal amount) {
        BigDecimal before = taxableIncome(profile, rules, status);
        BigDecimal after = before.subtract(amount).max(BigDecimal.ZERO);
        return rules.incomeTax(status, before).subtract(rules.incomeTax(status, after));
    }
}
