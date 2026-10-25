package com.taxdoc.planning.extraction;

import static com.taxdoc.planning.extraction.ExtractionFixtures.BASE_URL;
import static com.taxdoc.planning.extraction.ExtractionFixtures.json;
import static com.taxdoc.planning.extraction.ExtractionFixtures.replaceOnce;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import com.taxdoc.planning.api.ApiExceptionHandler;
import com.taxdoc.planning.extraction.ExtractionUnavailableException.Reason;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExtractionClientTest {

    private static final String CLIENT_10_URL = BASE_URL + "/api/v1/clients/client-10/documents";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ExtractionClient client = new ExtractionClient(builder.build());

    private HttpServer httpServer;

    @AfterEach
    void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    // --- successful responses -------------------------------------------------------------

    @Test
    void parsesARealExtractionResponse() {
        server.expect(requestTo(CLIENT_10_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(json("client-10"), MediaType.APPLICATION_JSON));

        ClientDocuments docs = client.documentsFor("client-10");

        server.verify();
        assertThat(docs.clientRef()).isEqualTo("client-10");
        assertThat(docs.warnings()).isEmpty();
        assertThat(docs.documents()).hasSize(2).allSatisfy(d -> {
            assertThat(d.taxYear()).isEqualTo(2026);
            assertThat(d.needsReview()).isFalse();
        });
        // Newest first, as extraction sends them: the second W-2 uploaded comes first.
        SourceDocument.W2 wabash = (SourceDocument.W2) docs.documents().getFirst();
        assertThat(wabash.employerName()).isEqualTo("Wabash Data Systems Inc");
        assertThat(wabash.box1Wages()).isEqualTo(new BigDecimal("76500.00"));
        assertThat(wabash.box3SsWages()).isEqualTo(new BigDecimal("82500.00"));
        assertThat(wabash.box4SsTax()).isEqualTo(new BigDecimal("5115.00"));
        assertThat(wabash.box5MedicareWages()).isEqualTo(new BigDecimal("82500.00"));
        assertThat(wabash.box12()).containsExactly(
                new SourceDocument.Box12Entry("D", new BigDecimal("6000.00")),
                new SourceDocument.Box12Entry("W", new BigDecimal("1500.00")));
        assertThat(wabash.box15State()).isEqualTo("IL");
    }

    @Test
    void parses1099Nec() {
        server.expect(requestTo(BASE_URL + "/api/v1/clients/client-03/documents"))
                .andRespond(withSuccess(json("client-03"), MediaType.APPLICATION_JSON));

        ClientDocuments docs = client.documentsFor("client-03");

        SourceDocument.Form1099Nec nec = docs.documents().stream()
                .filter(SourceDocument.Form1099Nec.class::isInstance)
                .map(SourceDocument.Form1099Nec.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(nec.payerName()).isEqualTo("Pixelforge Studio LLC");
        assertThat(nec.box1aNonemployeeComp()).isEqualTo(new BigDecimal("42000.00"));
        assertThat(nec.box6State()).isEqualTo("OH");
        assertThat(nec.taxYear()).isEqualTo(2026);
    }

    @Test
    void clientWithNoDocumentsIsAnEmptyListNotAnError() {
        server.expect(requestTo(BASE_URL + "/api/v1/clients/new-client/documents"))
                .andRespond(withSuccess(
                        "{\"client_ref\":\"new-client\",\"documents\":[],\"taxpayer_groups\":[],\"warnings\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.documentsFor("new-client").documents()).isEmpty();
    }

    @Test
    void unknownFormTypeIsLeftOutWithAWarning() {
        String body = replaceOnce(json("client-03"), "\"form_type\": \"1099-NEC\"", "\"form_type\": \"1099-MISC\"");
        server.expect(requestTo(BASE_URL + "/api/v1/clients/client-03/documents"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ClientDocuments docs = client.documentsFor("client-03");

        assertThat(docs.documents()).singleElement().isInstanceOf(SourceDocument.W2.class);
        assertThat(docs.warnings()).singleElement().asString()
                .contains("1099-MISC").contains("left out");
    }

    @Test
    void invalidClientRefIsRejectedBeforeAnyRequest() {
        assertThatThrownBy(() -> client.documentsFor("../documents"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client_ref");
        server.verify(); // no request expected, none made
    }

    // --- error responses ------------------------------------------------------------------

    @Test
    void serverErrorIsReportedAsErrorResponse() {
        server.expect(requestTo(CLIENT_10_URL)).andRespond(withServerError());

        assertFailure(Reason.ERROR_RESPONSE, "HTTP 500");
    }

    @Test
    void serviceUnavailableIsReportedAsErrorResponse() {
        server.expect(requestTo(CLIENT_10_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertFailure(Reason.ERROR_RESPONSE, "HTTP 503");
    }

    // --- malformed responses --------------------------------------------------------------

    @Test
    void nonJsonBodyIsMalformed() {
        respondWith("<html>Bad gateway</html>");

        assertMalformed("Unexpected character");
    }

    @Test
    void emptyBodyIsMalformed() {
        server.expect(requestTo(CLIENT_10_URL)).andRespond(withSuccess());

        assertFailure(Reason.MALFORMED_RESPONSE, "empty body");
    }

    @Test
    void moneyAsAJsonNumberIsRejected() {
        respondWith(replaceOnce(json("client-10"), "\"box1_wages\": \"76500.00\"", "\"box1_wages\": 76500.00"));

        assertMalformed("money must be a decimal string");
    }

    @Test
    void moneyThatIsNotANumberIsRejected() {
        respondWith(replaceOnce(json("client-10"), "\"box4_ss_tax\": \"5115.00\"", "\"box4_ss_tax\": \"5,115.00\""));

        assertMalformed("not a decimal amount: \"5,115.00\"");
    }

    @Test
    void missingRequiredMoneyFieldIsRejected() {
        respondWith(replaceOnce(json("client-10"), "\"box1_wages\": \"76500.00\",", ""));

        assertMalformed("box1_wages is missing");
    }

    @Test
    void responseForAnotherClientIsRejected() {
        respondWith(replaceOnce(json("client-10"), "\"client_ref\": \"client-10\",\n  \"documents\"",
                "\"client_ref\": \"client-01\",\n  \"documents\""));

        assertMalformed("different client");
    }

    // --- real HTTP: timeouts and connection failures ---------------------------------------

    @Test
    void realHttpRoundTripUsesTheConfiguredClient() throws IOException {
        startHttpServer(json("client-10"), null);

        ClientDocuments docs = realClient(Duration.ofSeconds(2)).documentsFor("client-10");

        assertThat(docs.documents()).hasSize(2);
    }

    @Test
    void slowResponseTimesOut() throws IOException {
        CountDownLatch release = new CountDownLatch(1);
        startHttpServer(json("client-10"), release);
        try {
            assertThatThrownBy(() -> realClient(Duration.ofMillis(300)).documentsFor("client-10"))
                    .isInstanceOfSatisfying(ExtractionUnavailableException.class,
                            e -> assertThat(e.reason()).isEqualTo(Reason.TIMEOUT))
                    .hasMessageContaining("did not respond in time");
        } finally {
            release.countDown();
        }
    }

    @Test
    void connectionRefusedIsUnreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        ExtractionClient refused = new ExtractionClient(ExtractionClient.restClient(RestClient.builder(),
                new ExtractionProperties(URI.create("http://127.0.0.1:" + closedPort), Duration.ofSeconds(1),
                        Duration.ofSeconds(1))));

        assertThatThrownBy(() -> refused.documentsFor("client-10"))
                .isInstanceOfSatisfying(ExtractionUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.UNREACHABLE));
    }

    @Test
    void unavailableMapsTo503WithReason() {
        ProblemDetail problem = new ApiExceptionHandler().extractionUnavailable(
                new ExtractionUnavailableException(Reason.TIMEOUT, "extraction-service did not respond in time", null));

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getTitle()).isEqualTo("Extraction service unavailable");
        assertThat(problem.getProperties()).containsEntry("reason", "TIMEOUT");
    }

    // --- helpers --------------------------------------------------------------------------

    private void respondWith(String body) {
        server.expect(requestTo(CLIENT_10_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertFailure(Reason reason, String messagePart) {
        assertThatThrownBy(() -> client.documentsFor("client-10"))
                .isInstanceOfSatisfying(ExtractionUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(reason))
                .hasMessageContaining(messagePart);
    }

    /**
     * Callers get a short message; the parser's detail is only in the cause (and the log), so
     * Jackson internals never reach the API's 503 body.
     */
    private void assertMalformed(String detail) {
        assertThatThrownBy(() -> client.documentsFor("client-10"))
                .isInstanceOfSatisfying(ExtractionUnavailableException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.MALFORMED_RESPONSE);
                    assertThat(e.getMessage()).isEqualTo("extraction-service's response is not valid");
                })
                .rootCause()
                .hasMessageContaining(detail);
    }

    /** JDK built-in server; if {@code release} is given, it waits for it before answering. */
    private void startHttpServer(String body, CountDownLatch release) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/v1/clients/client-10/documents", exchange -> {
            try {
                if (release != null) {
                    release.await(10, TimeUnit.SECONDS);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // The client gave up (timeout test); nothing to do.
            } finally {
                exchange.close();
            }
        });
        httpServer.start();
    }

    private ExtractionClient realClient(Duration readTimeout) {
        URI baseUrl = URI.create("http://127.0.0.1:" + httpServer.getAddress().getPort());
        return new ExtractionClient(ExtractionClient.restClient(RestClient.builder(),
                new ExtractionProperties(baseUrl, Duration.ofSeconds(1), readTimeout)));
    }
}
