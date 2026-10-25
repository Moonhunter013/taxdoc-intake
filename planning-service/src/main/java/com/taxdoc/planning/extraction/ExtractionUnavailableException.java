package com.taxdoc.planning.extraction;

/**
 * extraction-service could not give us a usable answer. Maps to HTTP 503; {@link #reason()}
 * says why, so callers (and tests) can tell a timeout from a bad response.
 */
public class ExtractionUnavailableException extends RuntimeException {

    public enum Reason {
        /** No response within the connect or read timeout. */
        TIMEOUT,
        /** Could not connect at all (service down, wrong host or port). */
        UNREACHABLE,
        /** Responded with an HTTP error status. */
        ERROR_RESPONSE,
        /** Responded 2xx, but the body is not the documents JSON we expect. */
        MALFORMED_RESPONSE
    }

    private final Reason reason;

    public ExtractionUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
