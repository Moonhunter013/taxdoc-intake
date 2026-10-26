package com.taxdoc.planning.persistence;

import com.taxdoc.planning.strategy.Outcome;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A generated plan for one client and tax year: the results of running every strategy.
 *
 * <p>{@code createdAt} is stored in UTC. The total is derived from the recommended results and
 * kept in sync by {@link #addResult}, so it can never disagree with them. Marginal results are
 * shown but not counted: the plan doesn't recommend them.
 */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "client_ref", nullable = false, length = 64)
    private String clientRef;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    /** True when any source document still needed human review when the plan was made. */
    @Column(nullable = false)
    private boolean provisional;

    @Column(name = "total_estimated_savings", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEstimatedSavings = BigDecimal.ZERO.setScale(2);

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Extraction's warnings (e.g. possible misfiling) and notes from building the profile. */
    @Convert(converter = AssumptionsConverter.class)
    @Column(nullable = false, length = 4000)
    private List<String> warnings = List.of();

    /** IDs of the source documents that made the plan provisional. */
    @Convert(converter = AssumptionsConverter.class)
    @Column(name = "documents_needing_review", nullable = false, length = 2000)
    private List<String> documentsNeedingReview = List.of();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("estimatedSavings DESC")
    private List<StrategyResultEntity> results = new ArrayList<>();

    protected Plan() {
        // for JPA
    }

    public Plan(String clientRef, int taxYear, boolean provisional, LocalDateTime createdAtUtc) {
        this(clientRef, taxYear, provisional, createdAtUtc, List.of(), List.of());
    }

    public Plan(
            String clientRef,
            int taxYear,
            boolean provisional,
            LocalDateTime createdAtUtc,
            List<String> warnings,
            List<String> documentsNeedingReview) {
        this.id = UUID.randomUUID().toString();
        this.clientRef = clientRef;
        this.taxYear = taxYear;
        this.provisional = provisional;
        this.createdAt = createdAtUtc;
        this.warnings = List.copyOf(warnings);
        this.documentsNeedingReview = List.copyOf(documentsNeedingReview);
    }

    public void addResult(StrategyResultEntity result) {
        result.attachTo(this);
        results.add(result);
        totalEstimatedSavings = results.stream()
                .filter(r -> r.getOutcome() == Outcome.RECOMMENDED)
                .map(StrategyResultEntity::getEstimatedSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }

    public String getId() {
        return id;
    }

    public String getClientRef() {
        return clientRef;
    }

    public int getTaxYear() {
        return taxYear;
    }

    public boolean isProvisional() {
        return provisional;
    }

    public BigDecimal getTotalEstimatedSavings() {
        return totalEstimatedSavings;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<String> getDocumentsNeedingReview() {
        return Collections.unmodifiableList(documentsNeedingReview);
    }

    public List<StrategyResultEntity> getResults() {
        return Collections.unmodifiableList(results);
    }
}
