package com.taxdoc.planning.plan;

import com.taxdoc.planning.extraction.ClientDocuments;
import com.taxdoc.planning.extraction.ExtractionClient;
import com.taxdoc.planning.persistence.Plan;
import com.taxdoc.planning.persistence.PlanRepository;
import com.taxdoc.planning.persistence.StrategyResultEntity;
import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.profile.TaxProfileBuilder;
import com.taxdoc.planning.rules.TaxRules;
import com.taxdoc.planning.rules.TaxYearRules;
import com.taxdoc.planning.strategy.StrategyEngine;
import com.taxdoc.planning.strategy.StrategyResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds, stores and loads plans: tax year -> extraction -> profile -> strategies -> database. */
@Service
public class PlanService {

    private final TaxRules taxRules;
    private final ExtractionClient extraction;
    private final StrategyEngine engine;
    private final PlanRepository plans;
    private final Clock clock;

    public PlanService(
            TaxRules taxRules, ExtractionClient extraction, StrategyEngine engine, PlanRepository plans, Clock clock) {
        this.taxRules = taxRules;
        this.extraction = extraction;
        this.engine = engine;
        this.plans = plans;
        this.clock = clock;
    }

    /**
     * Not one transaction on purpose: the extraction call happens before any database work, so a
     * slow extraction-service never holds a database connection open.
     *
     * @throws com.taxdoc.planning.rules.UnsupportedTaxYearException checked first, before any HTTP call
     * @throws com.taxdoc.planning.extraction.ExtractionUnavailableException if extraction can't answer
     * @throws NoDocumentsException if the client has no documents for that year
     */
    public PlanResponse create(String clientRef, int taxYear) {
        TaxYearRules rules = taxRules.forYear(taxYear);
        ClientDocuments documents = extraction.documentsFor(clientRef);
        TaxProfile profile = TaxProfileBuilder.build(documents, taxYear);
        if (profile.sourceDocuments().isEmpty()) {
            throw new NoDocumentsException(clientRef, taxYear, documents.documents().size());
        }
        List<StrategyResult> results = engine.evaluate(profile, rules);

        // Microseconds: MySQL DATETIME(6) rounds anything finer, and then GET would return a
        // different createdAt from the one POST returned.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        Plan plan = new Plan(clientRef, taxYear, profile.provisional(), now,
                profile.warnings(), profile.documentsNeedingReview().stream().map(UUID::toString).toList());
        for (StrategyResult r : results) {
            plan.addResult(new StrategyResultEntity(
                    r.code(), r.outcome(), r.title(), r.estimatedSavings(), r.explanation(), r.assumptions()));
        }
        return PlanResponse.from(plans.save(plan));
    }

    @Transactional(readOnly = true)
    public PlanResponse get(String id) {
        return plans.findWithResultsById(id).map(PlanResponse::from).orElseThrow(() -> new PlanNotFoundException(id));
    }
}
