package com.taxdoc.planning.plan;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plans", description = "Generate and read tax plans. Estimates are illustrative, not tax advice.")
public class PlanController {

    private static final String PROBLEM = "application/problem+json";

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    @PostMapping
    @Operation(summary = "Generate a plan",
            description = "Reads the client's documents from extraction-service, runs every strategy and stores "
                    + "the result. Results are sorted: recommended, marginal, not applicable; larger savings first.")
    @ApiResponse(responseCode = "201", description = "Plan created; Location points to it")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Tax year not configured, or no documents for the client",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "extraction-service unavailable (reason in the body)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        PlanResponse plan = plans.create(request.clientRef(), request.taxYear());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(plan.id())
                .toUri();
        return ResponseEntity.created(location).body(plan);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a plan", description = "Returns a stored plan exactly as it was generated.")
    @ApiResponse(responseCode = "200", description = "The plan")
    @ApiResponse(responseCode = "404", description = "No plan with that id",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    public PlanResponse get(@PathVariable String id) {
        return plans.get(id);
    }
}
