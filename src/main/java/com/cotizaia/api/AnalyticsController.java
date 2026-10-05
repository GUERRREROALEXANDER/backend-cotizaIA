package com.cotizaia.api;

import com.cotizaia.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes real agency dashboard metrics (project.txt section 2).
 * Calculation stays in the transactional service so HTTP responses contain only the resulting projection.
 */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping
    @Operation(summary = "Get agency analytics")
    public AnalyticsResponse get(CurrentUser user) {
        return AnalyticsResponse.from(analytics.get(user.agencyId()));
    }
}
