package com.taxdoc.planning.plan;

/**
 * Nothing to plan from: the client has no documents for the requested year. Maps to HTTP 422
 * rather than an empty plan, which would look like "no opportunities" when it means "no data".
 */
public class NoDocumentsException extends RuntimeException {

    private final String clientRef;
    private final int taxYear;

    public NoDocumentsException(String clientRef, int taxYear, int documentsForOtherYears) {
        super(documentsForOtherYears == 0
                ? "No documents found for client " + clientRef + ". Upload their W-2s and 1099-NECs first."
                : "Client " + clientRef + " has " + documentsForOtherYears + " document(s), but none for tax year "
                        + taxYear + ".");
        this.clientRef = clientRef;
        this.taxYear = taxYear;
    }

    public String clientRef() {
        return clientRef;
    }

    public int taxYear() {
        return taxYear;
    }
}
