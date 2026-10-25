package com.taxdoc.planning.extraction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * The wire format of {@code GET /api/v1/clients/{ref}/documents}, kept separate from the
 * {@link SourceDocument} types the rest of planning uses. Only the fields planning needs are
 * declared; anything else extraction adds (including the masked TINs) is ignored.
 */
final class ExtractionJson {

    private ExtractionJson() {}

    /** Own mapper, not Spring's: the money and null rules here are part of the contract. */
    static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            // Every BigDecimal in this format is money.
            .addModule(new SimpleModule("extraction-money").addDeserializer(BigDecimal.class, new MoneyDeserializer()))
            .build();

    record Response(String clientRef, List<Document> documents, List<String> warnings) {
        Response {
            required("client_ref", clientRef);
            documents = List.copyOf(required("documents", documents));
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    /** {@code document} stays a tree until we know the form type. */
    record Document(UUID documentId, String formType, Integer taxYear, Boolean needsReview, JsonNode document) {
        Document {
            required("document_id", documentId);
            required("form_type", formType);
            required("tax_year", taxYear);
            required("needs_review", needsReview);
            required("document", document);
        }
    }

    record W2Fields(
            String employerName,
            BigDecimal box1Wages,
            BigDecimal box3SsWages,
            BigDecimal box4SsTax,
            BigDecimal box5MedicareWages,
            List<Box12> box12,
            String box15State) {
        W2Fields {
            required("employer_name", employerName);
            required("box1_wages", box1Wages);
            required("box3_ss_wages", box3SsWages);
            required("box4_ss_tax", box4SsTax);
            required("box5_medicare_wages", box5MedicareWages);
            box12 = box12 == null ? List.of() : List.copyOf(box12);
        }
    }

    record Box12(String code, BigDecimal amount) {
        Box12 {
            required("box12.code", code);
            required("box12.amount", amount);
        }
    }

    record Form1099NecFields(String payerName, BigDecimal box1aNonemployeeComp, String box6State) {
        Form1099NecFields {
            required("payer_name", payerName);
            required("box1a_nonemployee_comp", box1aNonemployeeComp);
        }
    }

    /** IllegalArgumentException, which Jackson wraps, so a missing field reads as malformed JSON. */
    private static <T> T required(String field, T value) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value;
    }
}
