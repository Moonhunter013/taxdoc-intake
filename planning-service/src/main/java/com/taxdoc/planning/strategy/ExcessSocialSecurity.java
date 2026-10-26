package com.taxdoc.planning.strategy;

import static com.taxdoc.planning.strategy.Dollars.format;

import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.profile.TaxProfile.W2SocialSecurity;
import com.taxdoc.planning.rules.TaxYearRules;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Excess Social Security withheld by more than one employer. Each employer withholds up to the
 * wage base on its own, so two jobs can together withhold more than the annual maximum; the excess
 * is a refundable credit on the return. If one employer alone withheld too much, the credit doesn't
 * apply and that employer has to adjust it (https://www.irs.gov/taxtopics/tc608).
 *
 * <p>Not a judgement call: the money is owed to the client, so any excess is recommended.
 */
@Component
public final class ExcessSocialSecurity implements Strategy {

    static final String CODE = "EXCESS_SOCIAL_SECURITY";
    static final String TITLE = "Claim excess Social Security tax withheld";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public StrategyResult evaluate(TaxProfile profile, TaxYearRules rules) {
        List<W2SocialSecurity> w2s = profile.socialSecurityByW2();
        if (w2s.size() < 2) {
            return StrategyResult.notApplicable(CODE, TITLE,
                    "Fewer than two W-2s, so withholding can't have exceeded the annual maximum across employers.",
                    List.of());
        }
        BigDecimal maximum = rules.socialSecurity().maxEmployeeTax();
        List<String> listed = List.of(
                "The W-2s are from different employers. EINs are masked, so two W-2s from one employer can't be "
                        + "told apart.",
                "No railroad retirement (tier 1) tax, which shares the same maximum.");

        List<W2SocialSecurity> overWithheld = w2s.stream()
                .filter(w2 -> w2.taxWithheld().compareTo(maximum) > 0)
                .toList();
        if (!overWithheld.isEmpty()) {
            return StrategyResult.notApplicable(CODE, TITLE, overWithheld.getFirst().employerName()
                    + " alone withheld more than the " + format(maximum) + " maximum. That can't be claimed as a "
                    + "credit; ask the employer to correct it.", listed);
        }
        BigDecimal withheld = w2s.stream().map(W2SocialSecurity::taxWithheld).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal excess = withheld.subtract(maximum);
        if (excess.signum() <= 0) {
            return StrategyResult.notApplicable(CODE, TITLE, "Social Security withheld across " + w2s.size()
                    + " W-2s (" + format(withheld) + ") is within the " + format(maximum) + " maximum.", listed);
        }
        String explanation = w2s.size() + " employers withheld " + format(withheld) + " of Social Security tax, "
                + format(excess) + " more than the " + format(maximum) + " maximum for the year. The excess is a "
                + "refundable credit claimed on the federal return.";
        return StrategyResult.recommended(CODE, TITLE, excess, explanation, listed);
    }
}
