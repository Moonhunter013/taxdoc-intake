package com.taxdoc.planning.extraction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One extracted tax document, as planning sees it. Only the boxes planning uses are kept;
 * SSNs/EINs are not read at all, even masked (ADR-0005).
 */
public sealed interface SourceDocument permits SourceDocument.W2, SourceDocument.Form1099Nec {

    UUID documentId();

    int taxYear();

    /** True while extraction still wants a human to check a field (ADR-0004). */
    boolean needsReview();

    /** Form W-2. Box numbers are kept in the names so they can be checked against the form. */
    record W2(
            UUID documentId,
            int taxYear,
            boolean needsReview,
            String employerName,
            BigDecimal box1Wages,
            BigDecimal box3SsWages,
            BigDecimal box4SsTax,
            BigDecimal box5MedicareWages,
            List<Box12Entry> box12,
            String box15State)
            implements SourceDocument {

        public W2 {
            box12 = List.copyOf(box12);
        }
    }

    /** Form 1099-NEC (2026 layout: box 1a is nonemployee compensation). */
    record Form1099Nec(
            UUID documentId,
            int taxYear,
            boolean needsReview,
            String payerName,
            BigDecimal box1aNonemployeeComp,
            String box6State)
            implements SourceDocument {}

    record Box12Entry(String code, BigDecimal amount) {}
}
