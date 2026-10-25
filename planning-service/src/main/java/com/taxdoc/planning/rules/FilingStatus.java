package com.taxdoc.planning.rules;

/**
 * Filing statuses the planning engine supports. Married filing separately and qualifying
 * surviving spouse are not modelled yet (no sample client uses them).
 */
public enum FilingStatus {
    SINGLE,
    MARRIED_FILING_JOINTLY,
    HEAD_OF_HOUSEHOLD
}
