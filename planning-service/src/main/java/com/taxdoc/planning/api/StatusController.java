package com.taxdoc.planning.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Week 0 placeholder endpoint so the service has something to call end to end.
 * Infrastructure health checks use Actuator at /actuator/health instead.
 */
@RestController
@RequestMapping("/api/v1")
public class StatusController {

    public record Status(String service, String status) {}

    @GetMapping("/status")
    public Status status() {
        return new Status("planning-service", "ok");
    }
}
