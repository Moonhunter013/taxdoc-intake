package com.taxdoc.planning.extraction;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ExtractionProperties.class)
class ExtractionConfig {

    @Bean
    ExtractionClient extractionClient(ExtractionProperties properties) {
        return new ExtractionClient(ExtractionClient.restClient(RestClient.builder(), properties));
    }
}
