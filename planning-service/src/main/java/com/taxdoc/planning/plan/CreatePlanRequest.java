package com.taxdoc.planning.plan;

import com.taxdoc.planning.extraction.ExtractionClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/v1/plans}. Only the shape is checked here; whether the year is
 * configured is checked by the tax rules (422 with the supported years).
 */
@Schema(description = "Which client and tax year to plan")
public record CreatePlanRequest(
        @Schema(example = "client-02", description = "Advisor-assigned client reference (ADR-0006)")
        @NotNull
        @Pattern(regexp = ExtractionClient.CLIENT_REF_PATTERN,
                message = "must be 1-64 letters, digits, '-' or '_', starting with a letter or digit")
        String clientRef,
        @Schema(example = "2026")
        @NotNull
        @Min(2000)
        @Max(2100)
        Integer taxYear) {}
