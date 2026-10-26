package com.taxdoc.planning.plan;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.taxdoc.planning.MySqlIntegrationTest;
import com.taxdoc.planning.extraction.ExtractionFixtures;
import com.taxdoc.planning.persistence.PlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End to end: HTTP request -> extraction-service (WireMock serving real captured responses) ->
 * profile -> strategies -> MySQL (Testcontainers) -> HTTP response, then read back with GET.
 */
// Repeated from the base class so JUnit skips cleanly without Docker on every version.
@EnabledIf("com.taxdoc.planning.MySqlIntegrationTest#dockerAvailable")
@AutoConfigureMockMvc
class PlansApiTest extends MySqlIntegrationTest {

    private static final WireMockServer EXTRACTION =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        EXTRACTION.start();
    }

    /** Nanoseconds on purpose: MySQL keeps microseconds, so the service must truncate. */
    static final Instant NOW = Instant.parse("2026-10-26T12:00:00.123456789Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @DynamicPropertySource
    static void extraction(DynamicPropertyRegistry registry) {
        registry.add("taxdoc.extraction.base-url", EXTRACTION::baseUrl);
    }

    @AfterAll
    static void stopExtraction() {
        EXTRACTION.stop();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    PlanRepository plans;

    @BeforeEach
    void reset() {
        EXTRACTION.resetAll();
        plans.deleteAll();
    }

    // --- happy path ------------------------------------------------------------------------

    @Test
    void createsAPlanForClient02AndReadsItBack() throws Exception {
        stubDocuments("client-02", ExtractionFixtures.json("client-02"));

        MvcResult created = mvc.perform(createPlan("client-02", 2026))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/plans/")))
                .andExpect(jsonPath("$.clientRef").value("client-02"))
                .andExpect(jsonPath("$.taxYear").value(2026))
                .andExpect(jsonPath("$.provisional").value(false))
                .andExpect(jsonPath("$.provisionalNotice").value(nullValue()))
                // Week 2 done-criteria: S-corp election and Solo 401(k), with estimated savings.
                // Money is a string; 6,660.00 + 6,656.67 = 13,316.67 (recommended results only).
                .andExpect(jsonPath("$.totalEstimatedSavings").value("13316.67"))
                .andExpect(jsonPath("$.results", hasSize(5)))
                .andExpect(jsonPath("$.results[0].code").value("SOLO_401K"))
                .andExpect(jsonPath("$.results[0].outcome").value("RECOMMENDED"))
                .andExpect(jsonPath("$.results[0].estimatedSavings").value("6660.00"))
                .andExpect(jsonPath("$.results[1].code").value("S_CORP_ELECTION"))
                .andExpect(jsonPath("$.results[1].estimatedSavings").value("6656.67"))
                .andExpect(jsonPath("$.results[2].outcome").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.results[0].assumptions", hasSize(5)))
                .andExpect(jsonPath("$.createdAt").value("2026-10-26T12:00:00.123456Z"))
                .andExpect(jsonPath("$.disclaimer").value(containsString("not tax advice")))
                .andReturn();

        String location = created.getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);
        assertThat(created.getResponse().getContentAsString()).contains("\"id\":\"" + id + "\"");

        // GET returns exactly what POST returned, byte for byte.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(location))
                .andExpect(status().isOk())
                .andExpect(content().json(created.getResponse().getContentAsString(), true));
    }

    @Test
    void marginalResultsAreShownButNotCountedInTheTotal() throws Exception {
        // client-07: Solo 401(k) recommended (3,135.00), S-corp marginal (2,202.07). Total = 3,135.00 only.
        stubDocuments("client-07", ExtractionFixtures.json("client-07"));

        mvc.perform(createPlan("client-07", 2026))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalEstimatedSavings").value("3135.00"))
                .andExpect(jsonPath("$.results[0].code").value("SOLO_401K"))
                .andExpect(jsonPath("$.results[1].code").value("S_CORP_ELECTION"))
                .andExpect(jsonPath("$.results[1].outcome").value("MARGINAL"))
                .andExpect(jsonPath("$.results[1].estimatedSavings").value("2202.07"));
    }

    @Test
    void planBuiltOnDocumentsNeedingReviewSaysSoUpFront() throws Exception {
        // client-03's first document (the 1099-NEC) flagged for review; the "document" key follows the
        // document-level flag, so this can't hit a per-field needs_review.
        String body = ExtractionFixtures.replaceFirst(ExtractionFixtures.json("client-03"),
                "\"needs_review\": false,\n      \"document\": {", "\"needs_review\": true,\n      \"document\": {");
        stubDocuments("client-03", body);

        mvc.perform(createPlan("client-03", 2026))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provisional").value(true))
                .andExpect(jsonPath("$.provisionalNotice").value(containsString("still need review")))
                .andExpect(jsonPath("$.documentsNeedingReview", hasSize(1)));
    }

    @Test
    void extractionWarningsAreKeptWithThePlan() throws Exception {
        String body = ExtractionFixtures.replaceOnce(ExtractionFixtures.json("client-10"),
                "\"warnings\": []\n}", "\"warnings\": [\"Documents for this client belong to 2 different taxpayers\"]\n}");
        stubDocuments("client-10", body);

        mvc.perform(createPlan("client-10", 2026))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0]").value(containsString("2 different taxpayers")));
    }

    // --- errors ----------------------------------------------------------------------------

    @Test
    void unsupportedTaxYearIs422WithoutCallingExtraction() throws Exception {
        mvc.perform(createPlan("client-02", 2031))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unsupported tax year"))
                .andExpect(jsonPath("$.supportedYears[0]").value(2026));

        EXTRACTION.verify(0, getRequestedFor(anyUrl()));
    }

    @Test
    void extractionFailureIs503AndNothingIsStored() throws Exception {
        EXTRACTION.stubFor(get(urlEqualTo("/api/v1/clients/client-02/documents")).willReturn(serverError()));

        mvc.perform(createPlan("client-02", 2026))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Extraction service unavailable"))
                .andExpect(jsonPath("$.reason").value("ERROR_RESPONSE"));

        assertThat(plans.count()).isZero();
    }

    @Test
    void clientWithNoDocumentsIs422NotAnEmptyPlan() throws Exception {
        stubDocuments("new-client",
                "{\"client_ref\":\"new-client\",\"documents\":[],\"taxpayer_groups\":[],\"warnings\":[]}");

        mvc.perform(createPlan("new-client", 2026))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("No documents for client"))
                .andExpect(jsonPath("$.detail").value(containsString("No documents found for client new-client")));

        assertThat(plans.count()).isZero();
    }

    @Test
    void invalidRequestIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRef\": \"../secrets\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.clientRef").value(containsString("letters, digits")))
                .andExpect(jsonPath("$.errors.taxYear").value("must not be null"));

        EXTRACTION.verify(0, getRequestedFor(anyUrl()));
    }

    @Test
    void malformedJsonIs400ProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/plans").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownPlanIs404ProblemDetail() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/plans/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Plan not found"));
    }

    // --- OpenAPI ---------------------------------------------------------------------------

    @Test
    void openApiDocumentsBothEndpoints() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TaxDoc planning-service"))
                .andExpect(jsonPath("$.paths['/api/v1/plans'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/plans'].post.responses['422']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/plans'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/plans/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.PlanResponse.properties.totalEstimatedSavings.type")
                        .value("string"));
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    // --- helpers ---------------------------------------------------------------------------

    private static void stubDocuments(String clientRef, String body) {
        EXTRACTION.stubFor(get(urlEqualTo("/api/v1/clients/" + clientRef + "/documents")).willReturn(okJson(body)));
    }

    private static org.springframework.test.web.servlet.RequestBuilder createPlan(String clientRef, int taxYear) {
        return post("/api/v1/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRef\": \"" + clientRef + "\", \"taxYear\": " + taxYear + "}");
    }
}
