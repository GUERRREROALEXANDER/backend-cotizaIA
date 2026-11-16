package com.cotizaia.ingest.gmail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the automatic Gmail intake (spec 11). Everything secret comes
 * from the environment; nothing here has a usable default for production.
 *
 * @param enabled master switch; when false no Gmail bean exists at all
 * @param mailbox the mailbox address (cotiza@...), used as the cursor key and
 *        to reject push notifications for another mailbox
 * @param label the Gmail label a filter applies to quotable mail; only
 *        messages carrying it become briefs
 * @param agencyId the agency that owns the mailbox; every brief lands there
 * @param pollInterval how often the history cursor is read (PT1M in poll
 *        mode; a slower safety net such as PT15M in push mode)
 * @param schedulerEnabled turns the scheduled poll and watch renewal off
 *        (tests drive the service directly)
 * @param createUnknownSenders when true an unknown sender becomes a new client;
 *        when false the message is recorded as SKIPPED_UNKNOWN_SENDER
 * @param autoProcess when true the new brief is sent through the
 *        QuotationFacade right away
 * @param maxMessagesPerRun cap per sync, so a flood cannot exhaust the LLM quota
 * @param oauth OAuth client and refresh token with the gmail.readonly scope
 * @param push Pub/Sub push settings
 */
@ConfigurationProperties(prefix = "gmail")
public record GmailProperties(
        boolean enabled,
        String mailbox,
        String label,
        Long agencyId,
        Duration pollInterval,
        boolean schedulerEnabled,
        boolean createUnknownSenders,
        boolean autoProcess,
        int maxMessagesPerRun,
        OAuth oauth,
        Push push) {

    public GmailProperties {
        label = label == null || label.isBlank() ? "cotizar" : label;
        pollInterval = pollInterval == null ? Duration.ofMinutes(1) : pollInterval;
        maxMessagesPerRun = maxMessagesPerRun <= 0 ? 20 : maxMessagesPerRun;
        oauth = oauth == null ? new OAuth(null, null, null) : oauth;
        push = push == null ? new Push(false, null, null, null) : push;
    }

    public record OAuth(String clientId, String clientSecret, String refreshToken) {
    }

    /**
     * @param enabled when true the mailbox is watched (users.watch) and
     *        POST /api/ingest/email accepts Pub/Sub pushes
     * @param topic full topic name, projects/PROJECT/topics/TOPIC
     * @param audience the audience configured on the push subscription
     *        (the public endpoint URL); checked in the OIDC token
     * @param serviceAccountEmail the push subscription's service account;
     *        checked against the token's email claim
     */
    public record Push(boolean enabled, String topic, String audience, String serviceAccountEmail) {
    }
}
