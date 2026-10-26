package com.taxdoc.planning.strategy;

/** What a strategy concluded for one client. Ordered from most to least actionable. */
public enum Outcome {
    /** Worth doing: estimated savings clear the strategy's threshold. */
    RECOMMENDED,
    /** Saves something, but not clearly enough to recommend; the explanation says why. */
    MARGINAL,
    /** Doesn't fit this client (e.g. no self-employment income), or would cost more than it saves. */
    NOT_APPLICABLE
}
