package com.taxdoc.planning.extraction;

import com.taxdoc.planning.extraction.ExtractionUnavailableException.Reason;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;

/**
 * Reads a client's documents from extraction-service's API. Planning never reads extraction's
 * database (ADR-0003); this class is the whole boundary.
 *
 * <p>Every failure becomes an {@link ExtractionUnavailableException} with a {@link Reason}, so
 * callers handle one exception type and never see a half-parsed response.
 */
public class ExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(ExtractionClient.class);

    /** Same rule extraction applies (ADR-0006): safe in URLs and logs. */
    private static final Pattern CLIENT_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    private final RestClient restClient;

    public ExtractionClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** A {@code RestClient} for extraction-service with the configured timeouts. */
    public static RestClient restClient(RestClient.Builder builder, ExtractionProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                // Extraction runs on uvicorn, which serves HTTP/1.1 only. Pinning it stops the JDK
                // client from offering an HTTP/2 (h2c) upgrade on every plain-HTTP request.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * All of a client's documents, newest first. A client with no documents yet gets an empty
     * list, as extraction returns.
     *
     * @throws IllegalArgumentException if {@code clientRef} isn't a valid client reference
     * @throws ExtractionUnavailableException if extraction can't give a usable answer
     */
    public ClientDocuments documentsFor(String clientRef) {
        if (clientRef == null || !CLIENT_REF.matcher(clientRef).matches()) {
            throw new IllegalArgumentException(
                    "client_ref must be 1-64 letters, digits, '-' or '_', starting with a letter or digit");
        }
        return parse(clientRef, fetch(clientRef));
    }

    private String fetch(String clientRef) {
        try {
            return restClient.get()
                    .uri("/api/v1/clients/{clientRef}/documents", clientRef)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw failure(Reason.ERROR_RESPONSE, clientRef,
                    "extraction-service returned HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw failure(Reason.TIMEOUT, clientRef, "extraction-service did not respond in time", e);
            }
            throw failure(Reason.UNREACHABLE, clientRef, "could not connect to extraction-service", e);
        } catch (RestClientException e) {
            throw failure(Reason.MALFORMED_RESPONSE, clientRef, "could not read extraction-service's response", e);
        }
    }

    private ClientDocuments parse(String clientRef, String body) {
        if (body == null || body.isBlank()) {
            throw failure(Reason.MALFORMED_RESPONSE, clientRef, "extraction-service returned an empty body", null);
        }
        try {
            ExtractionJson.Response response = ExtractionJson.MAPPER.readValue(body, ExtractionJson.Response.class);
            if (!clientRef.equals(response.clientRef())) {
                throw new IllegalArgumentException("response is for a different client");
            }
            List<SourceDocument> documents = new ArrayList<>();
            List<String> warnings = new ArrayList<>(response.warnings());
            for (ExtractionJson.Document doc : response.documents()) {
                switch (doc.formType()) {
                    case "W-2" -> documents.add(toW2(doc));
                    case "1099-NEC" -> documents.add(toForm1099Nec(doc));
                    // Not an error: extraction may support more forms before planning does. The
                    // warning travels with the profile so the gap is visible, not silent.
                    default -> warnings.add("Document " + doc.documentId() + " is a " + doc.formType()
                            + " form, which planning does not use yet; it was left out.");
                }
            }
            return new ClientDocuments(clientRef, documents, warnings);
        } catch (JacksonException | IllegalArgumentException e) {
            // Parser detail (class names, offsets) goes to the log, not into the API's 503 body.
            String detail = e instanceof JacksonException je ? je.getOriginalMessage() : e.getMessage();
            log.warn("Invalid extraction response for client {}: {}", clientRef, detail);
            throw failure(Reason.MALFORMED_RESPONSE, clientRef, "extraction-service's response is not valid", e);
        }
    }

    private static SourceDocument.W2 toW2(ExtractionJson.Document doc) {
        ExtractionJson.W2Fields f = ExtractionJson.MAPPER.treeToValue(doc.document(), ExtractionJson.W2Fields.class);
        List<SourceDocument.Box12Entry> box12 = f.box12().stream()
                .map(e -> new SourceDocument.Box12Entry(e.code(), e.amount()))
                .toList();
        return new SourceDocument.W2(doc.documentId(), doc.taxYear(), doc.needsReview(), f.employerName(),
                f.box1Wages(), f.box3SsWages(), f.box4SsTax(), f.box5MedicareWages(), box12, f.box15State());
    }

    private static SourceDocument.Form1099Nec toForm1099Nec(ExtractionJson.Document doc) {
        ExtractionJson.Form1099NecFields f =
                ExtractionJson.MAPPER.treeToValue(doc.document(), ExtractionJson.Form1099NecFields.class);
        return new SourceDocument.Form1099Nec(doc.documentId(), doc.taxYear(), doc.needsReview(), f.payerName(),
                f.box1aNonemployeeComp(), f.box6State());
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static ExtractionUnavailableException failure(
            Reason reason, String clientRef, String message, Throwable cause) {
        // client_ref is validated and not sensitive; response bodies are never logged.
        log.warn("Extraction call failed for client {}: {} ({})", clientRef, message, reason);
        return new ExtractionUnavailableException(reason, message, cause);
    }
}
