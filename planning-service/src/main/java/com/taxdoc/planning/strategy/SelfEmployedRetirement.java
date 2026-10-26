package com.taxdoc.planning.strategy;

import static com.taxdoc.planning.strategy.Dollars.format;
import static com.taxdoc.planning.strategy.Dollars.percent;

import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.rules.FilingStatus;
import com.taxdoc.planning.rules.TaxYearRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Solo 401(k) (with SEP-IRA as the simpler alternative) for self-employment income.
 *
 * <ul>
 *   <li>Employer part: the plan rate (25%) converted to the self-employed rate, 0.25 / 1.25 = 20%,
 *       on net profit after the deduction for half of SE tax (IRS Publication 560, Rate Table and
 *       Deduction Worksheet for Self-Employed, https://www.irs.gov/publications/p560).
 *   <li>Employee part: the elective deferral limit, which is shared across every plan a person is
 *       in. When the client also has W-2 wages, that shared room is left to {@link WorkplaceRetirement}
 *       so the plan doesn't count it twice; only the employer part is used here.
 *   <li>The annual additions limit applies to this plan on its own, separately from an unrelated
 *       employer's plan (https://www.irs.gov/retirement-plans/plan-participant-employee/retirement-topics-401k-and-profit-sharing-plan-contribution-limits).
 * </ul>
 */
@Component
public final class SelfEmployedRetirement implements Strategy {

    static final String CODE = "SOLO_401K";
    static final String TITLE = "Open a Solo 401(k) for self-employment income";

    private static final FilingStatus FILING_STATUS = FilingStatus.SINGLE;

    private final ContributionAssumptions assumptions;

    public SelfEmployedRetirement(ContributionAssumptions assumptions) {
        this.assumptions = assumptions;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public StrategyResult evaluate(TaxProfile profile, TaxYearRules rules) {
        BigDecimal profit = profile.nonemployeeCompensation();
        BigDecimal netEarnings = PayrollTaxes.netEarnings(rules, profit);
        if (netEarnings.compareTo(rules.selfEmployment().minimumNetEarnings()) < 0) {
            return StrategyResult.notApplicable(CODE, TITLE,
                    "No self-employment income large enough to owe self-employment tax.", List.of());
        }
        TaxYearRules.Retirement limits = rules.retirement();

        BigDecimal planEarnings = profit.subtract(FederalIncomeTax.halfSelfEmploymentTax(profile, rules));
        BigDecimal selfEmployedRate = limits.employerContributionRate()
                .divide(BigDecimal.ONE.add(limits.employerContributionRate()), 6, RoundingMode.HALF_UP);
        BigDecimal employerPart = planEarnings.min(limits.compensationLimit()).multiply(selfEmployedRate);
        BigDecimal employeePart = profile.hasW2()
                ? BigDecimal.ZERO
                : limits.electiveDeferralLimit().subtract(profile.electiveDeferrals401k()).max(BigDecimal.ZERO);
        BigDecimal maximum = employerPart.add(employeePart).min(limits.annualAdditionsLimit()).min(planEarnings);
        BigDecimal sepMaximum = employerPart.min(limits.annualAdditionsLimit());

        BigDecimal target = profit.multiply(assumptions.savingsRate());
        BigDecimal contribution = maximum.min(target).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saved = FederalIncomeTax.taxSaved(profile, rules, FILING_STATUS, contribution);

        String explanation = "A Solo 401(k) could take up to " + format(maximum) + " of "
                + format(profit) + " in self-employment income (" + format(employerPart) + " as the employer"
                // Worded from the computed amount, so the text can't disagree with the calculation.
                + (employeePart.signum() == 0 ? "" : " plus " + format(employeePart) + " as the employee")
                + "). Contributing "
                + format(contribution) + ", " + percent(assumptions.savingsRate()) + " of that income, would lower "
                + "federal income tax by about " + format(saved) + ". A SEP-IRA is simpler to run but allows only "
                + "the employer part, up to " + format(sepMaximum) + ".";
        List<String> listed = List.of(
                "Saving target of " + percent(assumptions.savingsRate()) + " of self-employment income ("
                        + format(target) + "), capped by the plan limits.",
                "1099-NEC income is treated as net profit; business expenses aren't on the forms.",
                profile.hasW2()
                        ? "The employee deferral limit is shared with the W-2 401(k), so only the employer part is "
                                + "counted here; the workplace 401(k) result covers deferrals."
                        : "No other retirement plan; the forms show none.",
                "Run as a sole proprietorship. An S-corp election would base contributions on the salary instead.",
                "Under 50, so no catch-up; filing status single. Federal income tax only, without the QBI deduction.");
        return assumptions.result(CODE, TITLE, saved, explanation, listed);
    }
}
