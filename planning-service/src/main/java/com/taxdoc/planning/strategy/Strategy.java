package com.taxdoc.planning.strategy;

import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.rules.TaxYearRules;

/**
 * A tax-planning opportunity the engine checks for every client (ADR-0007).
 *
 * <p>Sealed: the set of strategies is a deliberate, reviewed list, not a plugin point. Adding
 * one means adding it to {@code permits}, which makes it show up in code review.
 *
 * <p>Implementations are pure: same profile and rules in, same result out. Every limit and rate
 * comes from {@link TaxYearRules}; judgement calls (e.g. a reasonable salary) come from
 * configuration and are listed in the result's assumptions.
 */
public sealed interface Strategy
        permits SCorpElection, SelfEmployedRetirement, WorkplaceRetirement, HsaContribution, ExcessSocialSecurity {

    /** Stable identifier, stored with plan results. */
    String code();

    StrategyResult evaluate(TaxProfile profile, TaxYearRules rules);
}
