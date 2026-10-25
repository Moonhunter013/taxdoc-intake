package com.taxdoc.planning.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Federal constants for one tax year, bound from {@code tax-years.yml}. Strategies get every
 * limit and rate from here and never use literals, so a new year is a config change.
 *
 * <p>Every record validates itself on construction: a missing or out-of-range value fails
 * application startup with the property name in the error.
 */
public record TaxYearRules(
        SocialSecurity socialSecurity,
        SelfEmployment selfEmployment,
        Retirement retirement,
        Hsa hsa,
        AmountByStatus standardDeduction,
        BracketsByStatus brackets) {

    public TaxYearRules {
        Checks.present("social-security", socialSecurity);
        Checks.present("self-employment", selfEmployment);
        Checks.present("retirement", retirement);
        Checks.present("hsa", hsa);
        Checks.present("standard-deduction", standardDeduction);
        Checks.present("brackets", brackets);
    }

    public BigDecimal standardDeduction(FilingStatus status) {
        return standardDeduction.get(status);
    }

    /** Regular federal income tax on {@code taxableIncome}, rounded to cents. */
    public BigDecimal incomeTax(FilingStatus status, BigDecimal taxableIncome) {
        List<TaxBracket> table = brackets.get(status);
        BigDecimal tax = BigDecimal.ZERO;
        for (int i = 0; i < table.size(); i++) {
            TaxBracket bracket = table.get(i);
            if (taxableIncome.compareTo(bracket.over()) <= 0) {
                break;
            }
            BigDecimal top = i + 1 < table.size() ? table.get(i + 1).over().min(taxableIncome) : taxableIncome;
            tax = tax.add(top.subtract(bracket.over()).multiply(bracket.rate()));
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /** Rate on the next dollar of taxable income. */
    public BigDecimal marginalRate(FilingStatus status, BigDecimal taxableIncome) {
        BigDecimal rate = BigDecimal.ZERO;
        for (TaxBracket bracket : brackets.get(status)) {
            if (taxableIncome.compareTo(bracket.over()) < 0) {
                break;
            }
            rate = bracket.rate();
        }
        return rate;
    }

    public record SocialSecurity(
            BigDecimal wageBase,
            BigDecimal employeeRate,
            BigDecimal medicareRate,
            BigDecimal additionalMedicareRate,
            AmountByStatus additionalMedicareThreshold) {

        public SocialSecurity {
            Checks.positive("social-security.wage-base", wageBase);
            Checks.rate("social-security.employee-rate", employeeRate);
            Checks.rate("social-security.medicare-rate", medicareRate);
            Checks.rate("social-security.additional-medicare-rate", additionalMedicareRate);
            Checks.present("social-security.additional-medicare-threshold", additionalMedicareThreshold);
        }

        /** Most Social Security tax one person can owe as an employee (wage base x rate). */
        public BigDecimal maxEmployeeTax() {
            return wageBase.multiply(employeeRate).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public record SelfEmployment(BigDecimal netEarningsFactor) {

        public SelfEmployment {
            Checks.rate("self-employment.net-earnings-factor", netEarningsFactor);
        }
    }

    public record Retirement(
            BigDecimal electiveDeferralLimit,
            BigDecimal catchUpAge50,
            BigDecimal catchUpAge60To63,
            BigDecimal annualAdditionsLimit,
            BigDecimal compensationLimit,
            BigDecimal employerContributionRate,
            BigDecimal iraLimit,
            BigDecimal iraCatchUp) {

        public Retirement {
            Checks.positive("retirement.elective-deferral-limit", electiveDeferralLimit);
            Checks.positive("retirement.catch-up-age-50", catchUpAge50);
            Checks.positive("retirement.catch-up-age-60-to-63", catchUpAge60To63);
            Checks.positive("retirement.annual-additions-limit", annualAdditionsLimit);
            Checks.positive("retirement.compensation-limit", compensationLimit);
            Checks.rate("retirement.employer-contribution-rate", employerContributionRate);
            Checks.positive("retirement.ira-limit", iraLimit);
            Checks.positive("retirement.ira-catch-up", iraCatchUp);
            if (electiveDeferralLimit.compareTo(annualAdditionsLimit) > 0) {
                throw new IllegalArgumentException(
                        "retirement.elective-deferral-limit must not exceed annual-additions-limit");
            }
        }

        /** 401(k) catch-up for someone who is {@code age} at the end of the tax year. */
        public BigDecimal catchUp(int age) {
            if (age >= 60 && age <= 63) {
                return catchUpAge60To63;
            }
            return age >= 50 ? catchUpAge50 : BigDecimal.ZERO;
        }
    }

    public record Hsa(BigDecimal selfOnlyLimit, BigDecimal familyLimit, BigDecimal catchUpAge55) {

        public Hsa {
            Checks.positive("hsa.self-only-limit", selfOnlyLimit);
            Checks.positive("hsa.family-limit", familyLimit);
            Checks.positive("hsa.catch-up-age-55", catchUpAge55);
        }
    }

    /** One amount per filing status, e.g. the standard deduction. */
    public record AmountByStatus(
            BigDecimal single, BigDecimal marriedFilingJointly, BigDecimal headOfHousehold) {

        public AmountByStatus {
            Checks.positive("single", single);
            Checks.positive("married-filing-jointly", marriedFilingJointly);
            Checks.positive("head-of-household", headOfHousehold);
        }

        public BigDecimal get(FilingStatus status) {
            return switch (status) {
                case SINGLE -> single;
                case MARRIED_FILING_JOINTLY -> marriedFilingJointly;
                case HEAD_OF_HOUSEHOLD -> headOfHousehold;
            };
        }
    }

    /** One rate table per filing status. */
    public record BracketsByStatus(
            List<TaxBracket> single,
            List<TaxBracket> marriedFilingJointly,
            List<TaxBracket> headOfHousehold) {

        public BracketsByStatus {
            single = checkTable("brackets.single", single);
            marriedFilingJointly = checkTable("brackets.married-filing-jointly", marriedFilingJointly);
            headOfHousehold = checkTable("brackets.head-of-household", headOfHousehold);
        }

        public List<TaxBracket> get(FilingStatus status) {
            return switch (status) {
                case SINGLE -> single;
                case MARRIED_FILING_JOINTLY -> marriedFilingJointly;
                case HEAD_OF_HOUSEHOLD -> headOfHousehold;
            };
        }

        /** Must start at 0, with thresholds and rates both strictly increasing. */
        private static List<TaxBracket> checkTable(String property, List<TaxBracket> table) {
            Checks.present(property, table);
            if (table.isEmpty() || table.getFirst().over().signum() != 0) {
                throw new IllegalArgumentException(property + " must start with a bracket over 0");
            }
            for (int i = 1; i < table.size(); i++) {
                TaxBracket previous = table.get(i - 1);
                TaxBracket current = table.get(i);
                if (current.over().compareTo(previous.over()) <= 0
                        || current.rate().compareTo(previous.rate()) <= 0) {
                    throw new IllegalArgumentException(property + " must be in increasing order; check entry "
                            + (i + 1) + " (over " + current.over() + ")");
                }
            }
            return List.copyOf(table);
        }
    }
}
