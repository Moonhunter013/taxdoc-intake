package com.taxdoc.planning.strategy;

import static com.taxdoc.planning.strategy.Dollars.format;

import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.rules.FilingStatus;
import com.taxdoc.planning.rules.TaxYearRules;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * HSA contributions up to the self-only limit, made through the employer's cafeteria plan. Box 12
 * code W already includes those, so the room is the limit minus code W. Through payroll they save
 * income tax and the employee's Social Security and Medicare (IRS Publication 969).
 *
 * <p>Eligibility needs high-deductible health plan coverage, which no tax form shows, so every
 * result states it as an assumption.
 */
@Component
public final class HsaContribution implements Strategy {

    static final String CODE = "HSA";
    static final String TITLE = "Contribute to a health savings account (HSA)";

    private static final FilingStatus FILING_STATUS = FilingStatus.SINGLE;

    private final ContributionAssumptions assumptions;

    public HsaContribution(ContributionAssumptions assumptions) {
        this.assumptions = assumptions;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public StrategyResult evaluate(TaxProfile profile, TaxYearRules rules) {
        if (!profile.hasW2()) {
            return StrategyResult.notApplicable(CODE, TITLE,
                    "No W-2 employer to contribute through payroll. (A self-employed person can still deduct HSA "
                            + "contributions, but that isn't estimated here.)", List.of());
        }
        BigDecimal limit = rules.hsa().selfOnlyLimit();
        BigDecimal contributed = profile.hsaContributions();
        List<String> listed = List.of(
                "Eligible for an HSA: covered only by a high-deductible health plan. Coverage isn't on any tax "
                        + "form, so check this first.",
                "Self-only coverage (" + format(limit) + " limit); family coverage allows "
                        + format(rules.hsa().familyLimit()) + ". Under 55, so no catch-up.",
                "Contributions go through the employer's cafeteria plan, so they also skip Social Security and "
                        + "Medicare.",
                "Filing status single; federal taxes only.");

        BigDecimal room = limit.subtract(contributed);
        if (room.signum() <= 0) {
            return StrategyResult.notApplicable(CODE, TITLE,
                    "Already contributing " + format(contributed) + ", the self-only limit.", listed);
        }
        BigDecimal incomeTax = FederalIncomeTax.taxSaved(profile, rules, FILING_STATUS, room);
        BigDecimal payrollTax = PayrollTaxes.employeeTaxSavedOnWageReduction(
                rules, FILING_STATUS, room, profile.medicareWages());
        BigDecimal saved = incomeTax.add(payrollTax);
        String explanation = (contributed.signum() == 0
                        ? "No HSA contributions on the W-2s."
                        : "Contributing " + format(contributed) + " of a possible " + format(limit) + ".")
                + " Adding " + format(room) + " through payroll would save about " + format(incomeTax)
                + " of federal income tax and " + format(payrollTax) + " of Social Security and Medicare, about "
                + format(saved) + " in total, if the client is eligible.";
        return assumptions.result(CODE, TITLE, saved, explanation, listed);
    }
}
