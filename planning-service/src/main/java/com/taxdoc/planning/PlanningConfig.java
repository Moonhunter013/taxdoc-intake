package com.taxdoc.planning;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PlanningConfig {

    /** UTC everywhere: plans store createdAt in UTC (see Plan). A bean so tests can fix the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Title and description for /v3/api-docs and Swagger UI (/swagger-ui.html). */
    @Bean
    OpenAPI planningOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TaxDoc planning-service")
                .version("v1")
                .description("Tax-planning strategies with estimated savings for a client's uploaded W-2s and "
                        + "1099-NECs. All data is synthetic; estimates are illustrative, not tax advice. "
                        + "Errors use RFC 9457 problem details."));
    }
}
