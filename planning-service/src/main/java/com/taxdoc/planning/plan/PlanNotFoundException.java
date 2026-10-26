package com.taxdoc.planning.plan;

/** No plan with that ID. Maps to HTTP 404. */
public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(String id) {
        // Plan IDs are server-generated UUIDs; anything else simply isn't found. Not echoed back
        // verbatim beyond a length limit, so a caller can't fill responses with arbitrary text.
        super("No plan with id " + (id.length() > 64 ? id.substring(0, 64) + "..." : id) + ".");
    }
}
