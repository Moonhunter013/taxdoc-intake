package com.taxdoc.planning.strategy;

import static com.taxdoc.planning.strategy.Dollars.format;
import static com.taxdoc.planning.strategy.Dollars.percent;

import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.rules.FilingStatus;
import com.taxdoc.planning.rules.TaxYearRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S-corp election for self-employment income. A sole proprietor pays self-employment tax on all
 * net earnings. An S corporation pays the owner a salary (payroll tax applies) and the rest of
 * the profit as distributions (no payroll tax). The estimate is the difference, minus the extra
 * cost of running the corporation.
 *
 * <p>Scope (ADR-0007): Social Security and Medicare only. Income tax effects, notably the QBI
 * deduction, are not modelled and every result says so.
 */
@Component
@EnableConfigurationProperties(SCorpAssumptions.class)
public final class SCorpElection implements Strategy {

    static final String CODE = "S_CORP_ELECTION";
    static final String TITLE = "Elect S-corporation status";

    /** clients.json has no filing status; CLAUDE.md: assume single. */
    private static final FilingStatus FILING_STATUS = FilingStatus.SINGLE;

    private final SCorpAssumptions assumptions;

    public SCorpElection(SCorpAssumptions assumptions) {
        this.assumptions = assumptions;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public StrategyResult evaluate(TaxProfile profile, TaxYearRules rules) {
        BigDecimal profit = profile.nonemployeeCompensation();
        if (profit.signum() == 0) {
            return StrategyResult.notApplicable(CODE, TITLE,
                    "No self-employment (1099-NEC) income, so there is no self-employment tax to reduce.", List.of());
        }

        BigDecimal w2SocialSecurityWages = profile.socialSecurityByW2().stream()
                .map(TaxProfile.W2SocialSecurity::wages)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal medicareWages = profile.medicareWages();

        BigDecimal asSoleProprietor = PayrollTaxes.selfEmploymentTax(rules, profit, w2SocialSecurityWages)
                .add(PayrollTaxes.additionalMedicareOnSelfEmployment(rules, FILING_STATUS, profit, medicareWages));
        BigDecimal salary = profit.multiply(assumptions.salaryRatio())
                .min(assumptions.salaryCap())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal asSCorp =
                PayrollTaxes.payrollTaxOnSalary(rules, FILING_STATUS, salary, w2SocialSecurityWages, medicareWages);

        BigDecimal taxSaved = asSoleProprietor.subtract(asSCorp);
        BigDecimal net = taxSaved.subtract(assumptions.annualComplianceCost());
        List<String> listed = assumptionsFor(profit, salary);

        String comparison = "As a sole proprietor, " + format(profit) + " of self-employment income means about "
                + format(asSoleProprietor) + " of Social Security and Medicare tax. As an S corporation paying a "
                + format(salary) + " salary, payroll taxes would be about " + format(asSCorp) + ", which saves "
                + format(taxSaved) + " a year before the estimated " + format(assumptions.annualComplianceCost())
                + " of extra costs.";

        if (net.signum() <= 0) {
            return StrategyResult.notApplicable(CODE, TITLE,
                    comparison + " The extra costs outweigh the tax saved.", listed);
        }
        if (net.compareTo(assumptions.minimumNetSavings()) < 0) {
            return StrategyResult.marginal(CODE, TITLE, net, comparison + " That leaves about " + format(net)
                    + " a year, below the " + format(assumptions.minimumNetSavings())
                    + " this engine requires to recommend it: the saving may not justify running payroll, "
                    + "filing a separate return and defending the salary as reasonable.", listed);
        }
        return StrategyResult.recommended(CODE, TITLE, net, comparison + " That leaves about " + format(net)
                + " a year. Worth discussing, including what salary is reasonable for the work.", listed);
    }

    private List<String> assumptionsFor(BigDecimal profit, BigDecimal salary) {
        return List.of(
                "1099-NEC income of " + format(profit) + " is treated as net profit; business expenses aren't "
                        + "on the forms.",
                "Salary of " + format(salary) + ": " + percent(assumptions.salaryRatio()) + " of profit, capped at "
                        + format(assumptions.salaryCap()) + ". The IRS requires reasonable compensation for the "
                        + "owner's services but sets no formula; an advisor should set it from the duties performed.",
                "Extra costs of " + format(assumptions.annualComplianceCost()) + " a year for payroll and a "
                        + "separate S-corp return.",
                "Filing status single and no other self-employment income; neither is on the forms.",
                "Federal Social Security and Medicare only. Income tax effects are not modelled, including the "
                        + "qualified business income (QBI) deduction, which a salary can reduce.",
                "State S-corp taxes and fees are not included.");
    }
}
