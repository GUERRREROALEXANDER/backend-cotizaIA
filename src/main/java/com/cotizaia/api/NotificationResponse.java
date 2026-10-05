package com.cotizaia.api;

import com.cotizaia.domain.Notification;
import java.time.Instant;

/** Public projection without persistence internals. */
public record NotificationResponse(Long id, Long proposalId, String channel, String eventType,
        String payload, Instant sentAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(),
                notification.getProposal() == null ? null : notification.getProposal().getId(),
                notification.getChannel(), notification.getEventType(), notification.getPayload(),
                notification.getSentAt());
    }
}
