package com.taxdoc.planning.extraction;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Real extraction-service responses, captured by {@code scripts/capture_extraction_fixtures.py}
 * from extraction's own app (with S3, Textract and the database faked).
 */
public final class ExtractionFixtures {

    static final String BASE_URL = "http://extraction.test";

    private ExtractionFixtures() {}

    /** The raw JSON body extraction returns for {@code clientRef}. */
    public static String json(String clientRef) {
        try (InputStream in = ExtractionFixtures.class.getResourceAsStream("/extraction/" + clientRef + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("no fixture for " + clientRef);
            }
            // git's autocrlf may check the file out with CRLF; tests edit the text, so normalise.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The fixture parsed by the real client, as planning code receives it. */
    public static ClientDocuments documents(String clientRef) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/v1/clients/" + clientRef + "/documents"))
                .andRespond(withSuccess(json(clientRef), MediaType.APPLICATION_JSON));
        ClientDocuments documents = new ExtractionClient(builder.build()).documentsFor(clientRef);
        server.verify();
        return documents;
    }

    /** Replaces text that must occur exactly once, so an edited fixture can't silently stay the same. */
    public static String replaceOnce(String json, String target, String replacement) {
        int first = json.indexOf(target);
        if (first < 0 || json.indexOf(target, first + 1) >= 0) {
            throw new IllegalArgumentException("expected exactly one occurrence of " + target);
        }
        return json.replace(target, replacement);
    }
}
