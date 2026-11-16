package com.cotizaia.ingest.gmail;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Clock for the intake: the history poll (every minute in poll mode, a slower
 * safety net in push mode) and, in push mode, the watch renewal. Gmail expires
 * a watch after 7 days; renewing daily keeps a margin for a day of downtime.
 */
public class GmailIngestScheduler {

    private final GmailIngestService ingest;

    private final boolean push;

    public GmailIngestScheduler(GmailIngestService ingest, boolean push) {
        this.ingest = ingest;
        this.push = push;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (push) {
            renewWatch();
        }
        ingest.requestSync();
    }

    @Scheduled(fixedDelayString = "${gmail.poll-interval:PT1M}", initialDelayString = "${gmail.poll-interval:PT1M}")
    public void poll() {
        ingest.syncQuietly();
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
    public void renewWatch() {
        if (push) {
            try {
                ingest.renewWatch();
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(GmailIngestScheduler.class)
                        .warn("Gmail watch renewal failed: {}", e.toString());
            }
        }
    }
}
