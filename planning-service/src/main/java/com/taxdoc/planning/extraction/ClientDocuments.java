package com.taxdoc.planning.extraction;

import java.util.List;

/**
 * A client's documents from extraction-service, plus its warnings (e.g. documents filed under
 * the wrong client) and notes about documents planning can't use.
 */
public record ClientDocuments(String clientRef, List<SourceDocument> documents, List<String> warnings) {

    public ClientDocuments {
        documents = List.copyOf(documents);
        warnings = List.copyOf(warnings);
    }
}
