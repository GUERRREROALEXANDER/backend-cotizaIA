package com.cotizaia.service.whatsapp;

import com.cotizaia.domain.WhatsappMessage;
import com.cotizaia.repository.WhatsappMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox the WhatsApp service drains. The backend runs where the WhatsApp session cannot (a PC, behind NAT), so it
 * never calls WhatsApp: it writes rows here inside the transaction of the event, and the service claims them over
 * HTTP. A message written here survives the service being off until it expires.
 */
public class WhatsappOutbox {

    /** Pending rows read per claim; enough to step over rows still leased to a crashed claim. */
    private static final int SCAN = 200;

    private final WhatsappMessageRepository messages;
    private final Clock clock;

    public WhatsappOutbox(WhatsappMessageRepository messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    /** Joins the caller's transaction, so the message exists only if the event that caused it commits. */
    @Transactional
    public WhatsappMessage queue(String phone, String body, Duration lifetime) {
        Instant now = clock.instant();
        return messages.save(new WhatsappMessage(phone, body, now, now.plus(lifetime)));
    }

    /** The oldest messages ready to send, leased to the caller for {@link WhatsappMessage#LEASE}. */
    @Transactional
    public List<WhatsappMessage> claim(int limit) {
        Instant now = clock.instant();
        List<WhatsappMessage> taken = new ArrayList<>();
        for (WhatsappMessage message : messages.lockByStatus(WhatsappMessage.Status.PENDING, PageRequest.of(0, SCAN))) {
            if (taken.size() >= limit) {
                break;
            }
            if (message.take(now)) {
                taken.add(message);
            }
        }
        return taken;
    }

    /** Records the outcome; false when there is no such message. Reporting one twice changes nothing. */
    @Transactional
    public boolean finish(Long messageId, String failure) {
        return messages.lockById(messageId).map(message -> {
            message.finish(failure, clock.instant());
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public long pending() {
        return messages.countByStatus(WhatsappMessage.Status.PENDING);
    }
}
