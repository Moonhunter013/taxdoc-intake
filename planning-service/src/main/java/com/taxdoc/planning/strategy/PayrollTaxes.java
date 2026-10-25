package com.taxdoc.planning.strategy;

import com.taxdoc.planning.rules.FilingStatus;
import com.taxdoc.planning.rules.TaxYearRules;
import java.math.BigDecimal;

/**
 * Social Security and Medicare tax, as a sole proprietor (self-employment tax) and as an
 * employee-owner on payroll. Every rate and limit comes from {@link TaxYearRules}. Amounts are
 * not rounded here; callers round the final estimate.
 */
final class PayrollTaxes {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private PayrollTaxes() {}

    /** Schedule SE line 4a: net profit x 0.9235 (zero for a loss). */
    static BigDecimal netEarnings(TaxYearRules rules, BigDecimal netProfit) {
        return netProfit.max(BigDecimal.ZERO).multiply(rules.selfEmployment().netEarningsFactor());
    }

    /**
     * Self-employment tax, following Schedule SE Part I
     * (https://www.irs.gov/pub/irs-pdf/f1040sse.pdf): none under the $400 minimum (line 4c);
     * Social Security on net earnings up to whatever part of the wage base W-2 wages haven't used
     * (lines 7-10); Medicare on all net earnings (line 11). The SE rates are both halves of the
     * payroll rates (12.4% = 2 x 6.2%, 2.9% = 2 x 1.45%), so they're derived, not configured.
     *
     * @param w2SocialSecurityWages W-2 box 3 total (Schedule SE line 8a)
     */
    static BigDecimal selfEmploymentTax(TaxYearRules rules, BigDecimal netProfit, BigDecimal w2SocialSecurityWages) {
        TaxYearRules.SocialSecurity ss = rules.socialSecurity();
        BigDecimal netEarnings = netEarnings(rules, netProfit);
        if (netEarnings.compareTo(rules.selfEmployment().minimumNetEarnings()) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal wageBaseLeft = ss.wageBase().subtract(w2SocialSecurityWages).max(BigDecimal.ZERO);
        BigDecimal socialSecurity = netEarnings.min(wageBaseLeft).multiply(ss.employeeRate().multiply(TWO));
        BigDecimal medicare = netEarnings.multiply(ss.medicareRate().multiply(TWO));
        return socialSecurity.add(medicare);
    }

    /**
     * Additional Medicare Tax on self-employment income (Form 8959 Part II): 0.9% of net earnings
     * above the threshold, where the threshold is first reduced by Medicare wages.
     */
    static BigDecimal additionalMedicareOnSelfEmployment(
            TaxYearRules rules, FilingStatus status, BigDecimal netProfit, BigDecimal medicareWages) {
        TaxYearRules.SocialSecurity ss = rules.socialSecurity();
        BigDecimal netEarnings = netEarnings(rules, netProfit);
        if (netEarnings.compareTo(rules.selfEmployment().minimumNetEarnings()) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal threshold = ss.additionalMedicareThreshold().get(status).subtract(medicareWages).max(BigDecimal.ZERO);
        return netEarnings.subtract(threshold).max(BigDecimal.ZERO).multiply(ss.additionalMedicareRate());
    }

    /**
     * The extra Social Security and Medicare tax, employee and employer shares together, from
     * paying {@code salary} through payroll on top of the client's existing W-2 wages.
     *
     * <ul>
     *   <li>Employer Social Security: up to the wage base per employer, never refunded.
     *   <li>Employee Social Security: withholding above the wage base across employers comes back
     *       as a credit (https://www.irs.gov/taxtopics/tc608), so only the unused part of the wage
     *       base costs anything.
     *   <li>Medicare: both shares on the whole salary, plus the employee's Additional Medicare Tax
     *       on Medicare wages above the threshold (only the increase the salary causes).
     * </ul>
     */
    static BigDecimal payrollTaxOnSalary(
            TaxYearRules rules,
            FilingStatus status,
            BigDecimal salary,
            BigDecimal w2SocialSecurityWages,
            BigDecimal medicareWages) {
        TaxYearRules.SocialSecurity ss = rules.socialSecurity();
        BigDecimal employerSocialSecurity = salary.min(ss.wageBase()).multiply(ss.employeeRate());
        BigDecimal wageBaseLeft = ss.wageBase().subtract(w2SocialSecurityWages).max(BigDecimal.ZERO);
        BigDecimal employeeSocialSecurity = salary.min(wageBaseLeft).multiply(ss.employeeRate());
        BigDecimal medicare = salary.multiply(ss.medicareRate().multiply(TWO));
        BigDecimal threshold = ss.additionalMedicareThreshold().get(status);
        BigDecimal additionalMedicare = medicareWages.add(salary).subtract(threshold).max(BigDecimal.ZERO)
                .subtract(medicareWages.subtract(threshold).max(BigDecimal.ZERO))
                .multiply(ss.additionalMedicareRate());
        return employerSocialSecurity.add(employeeSocialSecurity).add(medicare).add(additionalMedicare);
    }
}
