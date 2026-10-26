package com.taxdoc.planning.strategy;

import static com.taxdoc.planning.strategy.Dollars.format;
import static com.taxdoc.planning.strategy.Dollars.percent;

import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.rules.FilingStatus;
import com.taxdoc.planning.rules.TaxYearRules;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Workplace 401(k) headroom: pre-tax deferrals below both the annual limit and the assumed
 * savings target. Deferrals (box 12 D + AA) still owe Social Security and Medicare, so only income
 * tax is saved.
 */
@Component
@EnableConfigurationProperties(ContributionAssumptions.class)
public final class WorkplaceRetirement implements Strategy {

    static final String CODE = "WORKPLACE_401K";
    static final String TITLE = "Increase workplace 401(k) contributions";

    private static final FilingStatus FILING_STATUS = FilingStatus.SINGLE;

    private final ContributionAssumptions assumptions;

    public WorkplaceRetirement(ContributionAssumptions assumptions) {
        this.assumptions = assumptions;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public StrategyResult evaluate(TaxProfile profile, TaxYearRules rules) {
        if (!profile.hasW2()) {
            return StrategyResult.notApplicable(CODE, TITLE, "No W-2 employment, so no workplace plan.", List.of());
        }
        BigDecimal limit = rules.retirement().electiveDeferralLimit();
        BigDecimal deferred = profile.electiveDeferrals401k();
        BigDecimal pay = profile.medicareWages();
        BigDecimal target = pay.multiply(assumptions.savingsRate());
        List<String> listed = List.of(
                "Saving target of " + percent(assumptions.savingsRate()) + " of pay (" + format(target)
                        + " on W-2 Medicare wages of " + format(pay) + "), capped by the " + format(limit)
                        + " deferral limit.",
                "The employer offers a 401(k) plan; the W-2s show deferrals but not the plan's terms.",
                "Under 50, so no catch-up contributions; filing status single. Neither is on the forms.",
                "Federal income tax only: deferrals still owe Social Security and Medicare, state tax not included.");

        BigDecimal room = limit.subtract(deferred);
        if (room.signum() <= 0) {
            return StrategyResult.notApplicable(CODE, TITLE,
                    "Already deferring " + format(deferred) + ", the annual limit.", listed);
        }
        BigDecimal contribution = room.min(target.subtract(deferred));
        if (contribution.signum() <= 0) {
            return StrategyResult.notApplicable(CODE, TITLE, "Already deferring " + format(deferred) + ", at least "
                    + percent(assumptions.savingsRate()) + " of pay.", listed);
        }
        BigDecimal saved = FederalIncomeTax.taxSaved(profile, rules, FILING_STATUS, contribution);
        String explanation = "Deferring " + format(deferred) + " of a possible " + format(limit) + ". Adding "
                + format(contribution) + " to reach " + percent(assumptions.savingsRate())
                + " of pay would lower federal income tax by about " + format(saved) + ".";
        return assumptions.result(CODE, TITLE, saved, explanation, listed);
    }
}
