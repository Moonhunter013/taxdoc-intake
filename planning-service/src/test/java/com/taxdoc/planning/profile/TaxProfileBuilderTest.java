package com.taxdoc.planning.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.taxdoc.planning.extraction.ClientDocuments;
import com.taxdoc.planning.extraction.ExtractionFixtures;
import com.taxdoc.planning.extraction.SourceDocument;
import com.taxdoc.planning.extraction.SourceDocument.Box12Entry;
import com.taxdoc.planning.profile.TaxProfile.W2SocialSecurity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Expected values come from {@code samples/ground-truth/}; the documents are real extraction
 * responses for those clients (see {@link ExtractionFixtures}).
 */
class TaxProfileBuilderTest {

    @Test
    void client03CombinesAW2AndA1099() {
        ClientDocuments docs = ExtractionFixtures.documents("client-03");

        TaxProfile profile = TaxProfileBuilder.build(docs, 2026);

        assertThat(profile.clientRef()).isEqualTo("client-03");
        assertThat(profile.taxYear()).isEqualTo(2026);
        assertThat(profile.w2Wages()).isEqualTo(money("52250.00"));
        assertThat(profile.medicareWages()).isEqualTo(money("55000.00"));
        assertThat(profile.nonemployeeCompensation()).isEqualTo(money("42000.00"));
        assertThat(profile.electiveDeferrals401k()).isEqualTo(money("2750.00"));
        assertThat(profile.hsaContributions()).isEqualByComparingTo("0");
        assertThat(profile.socialSecurityByW2()).extracting(W2SocialSecurity::employerName,
                        W2SocialSecurity::wages, W2SocialSecurity::taxWithheld)
                .containsExactly(tuple(
                        "Riverbend Health Systems", money("55000.00"), money("3410.00")));
        assertThat(profile.states()).containsExactly("OH");
        assertThat(profile.provisional()).isFalse();
        assertThat(profile.documentsNeedingReview()).isEmpty();
        assertThat(profile.sourceDocuments()).hasSize(2);
        assertThat(profile.warnings()).isEmpty();
    }

    @Test
    void client10AddsUpTwoW2sAndKeepsSocialSecurityPerEmployer() {
        ClientDocuments docs = ExtractionFixtures.documents("client-10");

        TaxProfile profile = TaxProfileBuilder.build(docs, 2026);

        assertThat(profile.w2Wages()).isEqualTo(money("180500.00")); // 104,000 + 76,500
        assertThat(profile.medicareWages()).isEqualTo(money("194500.00")); // 112,000 + 82,500
        assertThat(profile.electiveDeferrals401k()).isEqualTo(money("14000.00")); // D 8,000 + D 6,000
        assertThat(profile.hsaContributions()).isEqualTo(money("1500.00")); // W on the second W-2
        assertThat(profile.nonemployeeCompensation()).isEqualByComparingTo("0");
        // Not summed: #23 needs each employer's withholding for the excess Social Security check.
        assertThat(profile.socialSecurityByW2())
                .extracting(W2SocialSecurity::employerName, W2SocialSecurity::wages, W2SocialSecurity::taxWithheld)
                .containsExactlyInAnyOrder(
                        tuple("Lakeshore Capital Group LLC", money("112000.00"),
                                money("6944.00")),
                        tuple("Wabash Data Systems Inc", money("82500.00"),
                                money("5115.00")));
        assertThat(profile.states()).containsExactly("IL");
        assertThat(profile.provisional()).isFalse();
    }

    @Test
    void documentNeedingReviewMakesTheProfileProvisional() {
        ClientDocuments docs = ExtractionFixtures.documents("client-03");
        SourceDocument.W2 w2 = onlyW2(docs);
        ClientDocuments withReview = replace(docs, w2, new SourceDocument.W2(w2.documentId(), w2.taxYear(), true,
                w2.employerName(), w2.box1Wages(), w2.box3SsWages(), w2.box4SsTax(), w2.box5MedicareWages(),
                w2.box12(), w2.box15State()));

        TaxProfile profile = TaxProfileBuilder.build(withReview, 2026);

        assertThat(profile.provisional()).isTrue();
        assertThat(profile.documentsNeedingReview()).containsExactly(w2.documentId());
        // Still included in the totals: provisional means "label the estimate", not "ignore".
        assertThat(profile.w2Wages()).isEqualTo(money("52250.00"));
    }

    @Test
    void documentForAnotherTaxYearIsLeftOutWithAWarning() {
        ClientDocuments docs = ExtractionFixtures.documents("client-03");
        SourceDocument.W2 w2 = onlyW2(docs);
        ClientDocuments mixed = replace(docs, w2, new SourceDocument.W2(w2.documentId(), 2025, false,
                w2.employerName(), w2.box1Wages(), w2.box3SsWages(), w2.box4SsTax(), w2.box5MedicareWages(),
                w2.box12(), w2.box15State()));

        TaxProfile profile = TaxProfileBuilder.build(mixed, 2026);

        assertThat(profile.w2Wages()).isEqualByComparingTo("0");
        assertThat(profile.socialSecurityByW2()).isEmpty();
        assertThat(profile.nonemployeeCompensation()).isEqualTo(money("42000.00"));
        assertThat(profile.sourceDocuments()).doesNotContain(w2.documentId()).hasSize(1);
        assertThat(profile.warnings()).singleElement().asString()
                .contains(w2.documentId().toString()).contains("tax year 2025").contains("left out");
    }

    @Test
    void rothDeferralsCountAndUnrelatedBox12CodesDont() {
        // AA = Roth 401(k), shares the deferral limit with D. DD (employer health coverage cost)
        // and C (group-term life) are informational and must not leak into either total.
        SourceDocument.W2 w2 = new SourceDocument.W2(UUID.randomUUID(), 2026, false, "Example Employer",
                money("100000.00"), money("100000.00"), money("6200.00"), money("100000.00"),
                List.of(new Box12Entry("D", money("10000.00")), new Box12Entry("AA", money("5000.00")),
                        new Box12Entry("DD", money("8400.00")), new Box12Entry("C", money("120.00")),
                        new Box12Entry("W", money("1000.00"))),
                "CA");

        TaxProfile profile = TaxProfileBuilder.build(new ClientDocuments("example", List.of(w2), List.of()), 2026);

        assertThat(profile.electiveDeferrals401k()).isEqualTo(money("15000.00"));
        assertThat(profile.hsaContributions()).isEqualTo(money("1000.00"));
    }

    @Test
    void clientWithNoDocumentsHasAnEmptyProfile() {
        TaxProfile profile = TaxProfileBuilder.build(new ClientDocuments("new-client", List.of(), List.of()), 2026);

        assertThat(profile.w2Wages()).isEqualByComparingTo("0");
        assertThat(profile.nonemployeeCompensation()).isEqualByComparingTo("0");
        assertThat(profile.states()).isEmpty();
        assertThat(profile.sourceDocuments()).isEmpty();
        assertThat(profile.provisional()).isFalse();
    }

    @Test
    void extractionWarningsAreKept() {
        ClientDocuments docs = ExtractionFixtures.documents("client-03");
        ClientDocuments warned = new ClientDocuments(docs.clientRef(), docs.documents(),
                List.of("Documents for this client belong to 2 different taxpayers"));

        assertThat(TaxProfileBuilder.build(warned, 2026).warnings())
                .containsExactly("Documents for this client belong to 2 different taxpayers");
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static SourceDocument.W2 onlyW2(ClientDocuments docs) {
        return docs.documents().stream()
                .filter(SourceDocument.W2.class::isInstance)
                .map(SourceDocument.W2.class::cast)
                .reduce((a, b) -> {
                    throw new AssertionError("expected one W-2");
                })
                .orElseThrow();
    }

    private static ClientDocuments replace(ClientDocuments docs, SourceDocument old, SourceDocument updated) {
        List<SourceDocument> documents = new ArrayList<>(docs.documents());
        int index = documents.indexOf(old);
        assertThat(index).as("document to replace").isNotNegative();
        documents.set(index, updated);
        return new ClientDocuments(docs.clientRef(), documents, docs.warnings());
    }
}
