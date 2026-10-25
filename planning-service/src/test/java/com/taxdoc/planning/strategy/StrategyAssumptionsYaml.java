package com.taxdoc.planning.strategy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Binds the real {@code strategy-assumptions.yml} as Spring Boot does, without starting the app. */
final class StrategyAssumptionsYaml {

    private StrategyAssumptionsYaml() {}

    static Map<String, String> properties() {
        try {
            Map<String, String> flat = new LinkedHashMap<>();
            for (PropertySource<?> source : new YamlPropertySourceLoader()
                    .load("strategy-assumptions", new ClassPathResource("strategy-assumptions.yml"))) {
                ((Map<?, ?>) source.getSource()).forEach((k, v) -> flat.put(k.toString(), v.toString()));
            }
            return flat;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static SCorpAssumptions sCorp(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("taxdoc.strategies.s-corp", Bindable.of(SCorpAssumptions.class))
                .get();
    }

    static SCorpAssumptions sCorp() {
        return sCorp(properties());
    }
}
