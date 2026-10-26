package com.taxdoc.planning.plan;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.taxdoc.planning.persistence.Plan;
import com.taxdoc.planning.persistence.StrategyResultEntity;
import com.taxdoc.planning.strategy.Outcome;
import com.taxdoc.planning.strategy.StrategyResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/**
 * A plan as the API returns it. Money is a decimal string ("6656.67"), as in extraction's API:
 * a JSON number would be read as a floating-point double by JavaScript clients.
 *
 * <p>{@code provisional} and {@code provisionalNotice} come first on purpose: a plan built on
 * documents that still need review must say so before anything else (ADR-0003).
 */
@Schema(description = "A client's tax plan for one year")
public record PlanResponse(
        String id,
        String clientRef,
        int taxYear,
        @Schema(description = "True if any source document still needs human review")
        boolean provisional,
        @Schema(description = "Shown prominently when the plan is provisional; null otherwise", nullable = true)
        String provisionalNotice,
        List<String> documentsNeedingReview,
        @Schema(type = "string", example = "13316.67", description = "Sum of RECOMMENDED results only")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalEstimatedSavings,
        List<Result> results,
        @Schema(description = "From extraction (e.g. possible misfiling) and from building the profile")
        List<String> warnings,
        OffsetDateTime createdAt,
        String disclaimer) {

    static final String PROVISIONAL_NOTICE = "Provisional: some source documents still need review. "
            + "These estimates may change once the flagged fields are checked.";

    /** Same order as the engine: recommended, marginal, not applicable; larger savings first. */
    private static final Comparator<StrategyResultEntity> ORDER = Comparator.comparing(StrategyResultEntity::getOutcome)
            .thenComparing(StrategyResultEntity::getEstimatedSavings, Comparator.reverseOrder())
            .thenComparing(StrategyResultEntity::getStrategyCode);

    @Schema(description = "One strategy's conclusion")
    public record Result(
            String code,
            String title,
            Outcome outcome,
            @Schema(type = "string", example = "6656.67") @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal estimatedSavings,
            String explanation,
            List<String> assumptions) {}

    static PlanResponse from(Plan plan) {
        List<Result> results = plan.getResults().stream()
                .sorted(ORDER)
                .map(r -> new Result(r.getStrategyCode(), r.getTitle(), r.getOutcome(), r.getEstimatedSavings(),
                        r.getExplanation(), r.getAssumptions()))
                .toList();
        return new PlanResponse(plan.getId(), plan.getClientRef(), plan.getTaxYear(), plan.isProvisional(),
                plan.isProvisional() ? PROVISIONAL_NOTICE : null, plan.getDocumentsNeedingReview(),
                plan.getTotalEstimatedSavings(), results, plan.getWarnings(),
                plan.getCreatedAt().atOffset(ZoneOffset.UTC), StrategyResult.DISCLAIMER);
    }
}
