package com.taxdoc.planning.extraction;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where extraction-service lives and how long to wait for it ({@code taxdoc.extraction.*}).
 * Explicit timeouts matter: the JDK HTTP client waits forever by default, so a hung extraction
 * service would hang every plan request with it (ADR-0003).
 */
@ConfigurationProperties(prefix = "taxdoc.extraction")
public record ExtractionProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {

    public ExtractionProperties {
        if (baseUrl == null) {
            throw new IllegalArgumentException("taxdoc.extraction.base-url is missing");
        }
        connectTimeout = positive("taxdoc.extraction.connect-timeout", connectTimeout);
        readTimeout = positive("taxdoc.extraction.read-timeout", readTimeout);
    }

    private static Duration positive(String property, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " must be a positive duration, was " + value);
        }
        return value;
    }
}
