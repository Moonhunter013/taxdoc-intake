package com.taxdoc.planning.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Binds the real {@code tax-years.yml} the same way Spring Boot does at startup, without
 * starting the application (so these tests need no database).
 */
final class TaxYearsYaml {

    static final String PREFIX = "taxdoc.tax-rules";
    static final String Y2026 = PREFIX + ".years.2026.";

    private TaxYearsYaml() {}

    /** The flattened properties from the file, e.g. "taxdoc.tax-rules.years.2026.hsa.family-limit". */
    static Map<String, String> properties() {
        try {
            Map<String, String> flat = new LinkedHashMap<>();
            for (PropertySource<?> source :
                    new YamlPropertySourceLoader().load("tax-years", new ClassPathResource("tax-years.yml"))) {
                ((Map<?, ?>) source.getSource()).forEach((k, v) -> flat.put(k.toString(), v.toString()));
            }
            return flat;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Replaces one 2026 value, e.g. {@code set(props, "hsa.family-limit", "1")}. */
    static void set(Map<String, String> properties, String suffix, String value) {
        properties.put(keyFor(properties, suffix), value);
    }

    /** Removes one 2026 value, e.g. {@code remove(props, "retirement.ira-limit")}. */
    static void remove(Map<String, String> properties, String suffix) {
        properties.remove(keyFor(properties, suffix));
    }

    /**
     * Finds the actual key for a 2026 property. Spring may write map keys and list indexes as
     * "years.2026" or "years[2026]", so keys are compared in a normalised form. Fails unless
     * exactly one key matches, so a test can't silently modify nothing.
     */
    private static String keyFor(Map<String, String> properties, String suffix) {
        String wanted = normalise(Y2026 + suffix);
        List<String> matches = properties.keySet().stream()
                .filter(k -> normalise(k).equals(wanted))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one key for " + Y2026 + suffix + ", found "
                    + matches + ". Keys look like: " + properties.keySet().stream().limit(3).toList());
        }
        return matches.getFirst();
    }

    private static String normalise(String key) {
        return key.replace("[", ".").replace("]", "");
    }

    static TaxRulesProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind(PREFIX, Bindable.of(TaxRulesProperties.class))
                .get();
    }

    static TaxRules load() {
        return new TaxRules(bind(properties()));
    }
}
