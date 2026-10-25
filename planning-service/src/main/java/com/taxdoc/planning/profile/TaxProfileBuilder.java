package com.taxdoc.planning.profile;

import com.taxdoc.planning.extraction.ClientDocuments;
import com.taxdoc.planning.extraction.SourceDocument;
import com.taxdoc.planning.extraction.SourceDocument.Box12Entry;
import com.taxdoc.planning.profile.TaxProfile.W2SocialSecurity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Turns a client's documents into a {@link TaxProfile} for one tax year. Pure: no I/O, so the
 * aggregation rules are tested directly against the sample clients.
 */
public final class TaxProfileBuilder {

    /**
     * W-2 box 12 codes, from the 2026 General Instructions for Forms W-2 and W-3
     * (https://www.irs.gov/instructions/iw2w3): D = elective deferrals to a 401(k), AA =
     * designated Roth contributions to a 401(k). Both count toward the same elective deferral
     * limit, so they are summed.
     */
    static final Set<String> CODES_401K = Set.of("D", "AA");

    /**
     * W = employer contributions to an HSA, "including amounts the employee elected to
     * contribute using a section 125 (cafeteria) plan" (same source).
     */
    static final String CODE_HSA = "W";

    private TaxProfileBuilder() {}

    /**
     * Documents for other tax years are left out with a warning, never added in: mixing years
     * would apply one year's limits to another year's income.
     */
    public static TaxProfile build(ClientDocuments client, int taxYear) {
        BigDecimal w2Wages = BigDecimal.ZERO;
        BigDecimal medicareWages = BigDecimal.ZERO;
        BigDecimal deferrals401k = BigDecimal.ZERO;
        BigDecimal hsa = BigDecimal.ZERO;
        BigDecimal nonemployeeComp = BigDecimal.ZERO;
        List<W2SocialSecurity> socialSecurity = new ArrayList<>();
        Set<String> states = new TreeSet<>();
        List<UUID> needingReview = new ArrayList<>();
        List<UUID> included = new ArrayList<>();
        List<String> warnings = new ArrayList<>(client.warnings());

        for (SourceDocument doc : client.documents()) {
            if (doc.taxYear() != taxYear) {
                warnings.add("Document " + doc.documentId() + " is for tax year " + doc.taxYear()
                        + ", not " + taxYear + "; it was left out.");
                continue;
            }
            included.add(doc.documentId());
            if (doc.needsReview()) {
                needingReview.add(doc.documentId());
            }
            switch (doc) {
                case SourceDocument.W2 w2 -> {
                    w2Wages = w2Wages.add(w2.box1Wages());
                    medicareWages = medicareWages.add(w2.box5MedicareWages());
                    socialSecurity.add(new W2SocialSecurity(
                            w2.documentId(), w2.employerName(), w2.box3SsWages(), w2.box4SsTax()));
                    for (Box12Entry entry : w2.box12()) {
                        if (CODES_401K.contains(entry.code())) {
                            deferrals401k = deferrals401k.add(entry.amount());
                        } else if (CODE_HSA.equals(entry.code())) {
                            hsa = hsa.add(entry.amount());
                        }
                    }
                    addState(states, w2.box15State());
                }
                case SourceDocument.Form1099Nec nec -> {
                    nonemployeeComp = nonemployeeComp.add(nec.box1aNonemployeeComp());
                    addState(states, nec.box6State());
                }
            }
        }

        return new TaxProfile(client.clientRef(), taxYear, w2Wages, socialSecurity, medicareWages, deferrals401k,
                hsa, nonemployeeComp, new TreeSet<>(states), !needingReview.isEmpty(), needingReview, included,
                warnings);
    }

    private static void addState(Set<String> states, String state) {
        if (state != null && !state.isBlank()) {
            states.add(state);
        }
    }
}
