package com.cotizaia.domain;

/**
 * Input channel a {@link Brief} arrived through (project.txt section 6 pattern
 * 5a: Email / WhatsApp / web form). Each channel has its own payload shape; the
 * channel value is stored in {@code briefs.source} and constrained by
 * {@code ck_briefs_source}.
 *
 * <p>This enum names the known channels. Adding a channel is an additive
 * change: a new constant here (the channel's identity) plus one new
 * {@code BriefSource} adapter bean. The ingest service, registry and API never
 * branch on the channel, so none of them is edited.
 */
public enum BriefChannel {
    EMAIL,
    WHATSAPP,
    WEB_FORM
}
