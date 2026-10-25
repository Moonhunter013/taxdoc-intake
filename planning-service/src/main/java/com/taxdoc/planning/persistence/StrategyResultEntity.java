package com.taxdoc.planning.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** One strategy's outcome within a {@link Plan}. */
@Entity
@Table(name = "strategy_results")
public class StrategyResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "strategy_code", nullable = false, length = 64)
    private String strategyCode;

    @Column(nullable = false)
    private boolean applicable;

    @Column(name = "estimated_savings", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedSavings;

    @Column(nullable = false, length = 2000)
    private String explanation;

    @Convert(converter = AssumptionsConverter.class)
    @Column(nullable = false, length = 4000)
    private List<String> assumptions;

    protected StrategyResultEntity() {
        // for JPA
    }

    public StrategyResultEntity(
            String strategyCode,
            boolean applicable,
            BigDecimal estimatedSavings,
            String explanation,
            List<String> assumptions) {
        this.strategyCode = strategyCode;
        this.applicable = applicable;
        // Strategies may compute more decimals; store whole cents, rounding half up.
        this.estimatedSavings = estimatedSavings.setScale(2, RoundingMode.HALF_UP);
        this.explanation = explanation;
        this.assumptions = List.copyOf(assumptions);
    }

    void attachTo(Plan plan) {
        this.plan = plan;
    }

    public Long getId() {
        return id;
    }

    public String getStrategyCode() {
        return strategyCode;
    }

    public boolean isApplicable() {
        return applicable;
    }

    public BigDecimal getEstimatedSavings() {
        return estimatedSavings;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }
}
