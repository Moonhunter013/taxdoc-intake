package com.taxdoc.planning.strategy;

import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.rules.TaxYearRules;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Runs every strategy against a profile. */
@Service
public class StrategyEngine {

    /** Recommendations first, then marginal, then not applicable; larger savings first within each. */
    static final Comparator<StrategyResult> ORDER = Comparator.comparing(StrategyResult::outcome)
            .thenComparing(StrategyResult::estimatedSavings, Comparator.reverseOrder())
            .thenComparing(StrategyResult::code);

    private final List<Strategy> strategies;

    public StrategyEngine(List<Strategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public List<StrategyResult> evaluate(TaxProfile profile, TaxYearRules rules) {
        return strategies.stream()
                .map(strategy -> strategy.evaluate(profile, rules))
                .sorted(ORDER)
                .toList();
    }
}
