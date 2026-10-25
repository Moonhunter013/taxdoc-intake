package com.taxdoc.planning.profile;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * One clean picture of a client's income for a tax year, built from their extracted W-2s and
 * 1099-NECs. Strategies work on this, never on raw documents. All amounts are exact
 * {@code BigDecimal}s, summed as reported (no rounding).
 *
 * @param w2Wages total W-2 box 1 (federal taxable wages)
 * @param socialSecurityByW2 box 3/4 per W-2; excess Social Security is worked out per employer
 * @param medicareWages total W-2 box 5
 * @param electiveDeferrals401k box 12 codes D + AA: pre-tax and Roth 401(k) deferrals, which share
 *     one annual limit
 * @param hsaContributions box 12 code W: employer HSA contributions, including what the employee
 *     put in through a cafeteria plan
 * @param nonemployeeCompensation total 1099-NEC box 1a
 * @param states state codes from W-2 box 15 and 1099-NEC box 6
 * @param provisional true if any document still needs human review: estimates built on this
 *     profile must say so (ADR-0003)
 * @param documentsNeedingReview the documents that make the profile provisional
 * @param sourceDocuments every document included in the totals
 * @param warnings from extraction (e.g. possible misfiling) and from building the profile
 */
public record TaxProfile(
        String clientRef,
        int taxYear,
        BigDecimal w2Wages,
        List<W2SocialSecurity> socialSecurityByW2,
        BigDecimal medicareWages,
        BigDecimal electiveDeferrals401k,
        BigDecimal hsaContributions,
        BigDecimal nonemployeeCompensation,
        SortedSet<String> states,
        boolean provisional,
        List<UUID> documentsNeedingReview,
        List<UUID> sourceDocuments,
        List<String> warnings) {

    public TaxProfile {
        socialSecurityByW2 = List.copyOf(socialSecurityByW2);
        states = Collections.unmodifiableSortedSet(new TreeSet<>(states));
        documentsNeedingReview = List.copyOf(documentsNeedingReview);
        sourceDocuments = List.copyOf(sourceDocuments);
        warnings = List.copyOf(warnings);
    }

    /**
     * Social Security wages and tax withheld on one W-2. Kept per W-2 rather than summed: the
     * excess Social Security credit applies when several employers together withheld too much,
     * while excess withheld by a single employer is for that employer to adjust
     * (https://www.irs.gov/taxtopics/tc608).
     *
     * <p>One entry per W-2, not grouped by employer: EINs arrive masked, so two W-2s from the same
     * employer can't be told apart reliably. #23 decides how to treat that.
     */
    public record W2SocialSecurity(UUID documentId, String employerName, BigDecimal wages, BigDecimal taxWithheld) {}
}
