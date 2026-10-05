package com.cotizaia.api;

import com.cotizaia.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes a bounded agency dashboard feed (project.txt section 2).
 * The service filters dashboard deliveries so simulated email and analytics observers do not duplicate entries.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "List recent dashboard notifications")
    public List<NotificationResponse> list(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            CurrentUser user) {
        return notifications.dashboard(user.agencyId(), limit).stream().map(NotificationResponse::from).toList();
    }
}
