package com.cotizaia.ingest.gmail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a Gmail {@code users.messages.get?format=full} resource into a
 * {@link GmailMessage}. Pure function, no I/O, so the MIME edge cases are unit
 * tested without Gmail.
 *
 * <p>Body rule: the first text/plain part wins; with none, the first text/html
 * part with tags stripped. The quoted part of a reply ("On ... wrote:" or
 * lines starting with "&gt;") is dropped, and the result is capped at
 * {@link #MAX_BODY_CHARS}, the EmailBriefAdapter's own limit, so a long thread
 * becomes a brief instead of a rejected payload.
 */
public final class GmailMessageParser {

    static final int MAX_BODY_CHARS = 5_000;

    private static final Pattern ANGLE_ADDRESS = Pattern.compile("^\\s*\"?([^\"<]*?)\"?\\s*<([^>]+)>\\s*$");

    private static final Pattern REPLY_HEADER = Pattern.compile(
            "^(On .+ wrote:|El .+ escribi[oó]:|-----Original Message-----|-----Mensaje original-----)\\s*$");

    private GmailMessageParser() {
    }

    @SuppressWarnings("unchecked")
    public static GmailMessage parse(Map<String, Object> resource) {
        Map<String, Object> payload = (Map<String, Object>) resource.getOrDefault("payload", Map.of());
        String from = header(payload, "From");
        String address = from;
        String name = from;
        Matcher matcher = ANGLE_ADDRESS.matcher(from);
        if (matcher.matches()) {
            address = matcher.group(2).trim();
            name = matcher.group(1).isBlank() ? address : matcher.group(1).trim();
        }
        address = address.trim().toLowerCase(Locale.ROOT);

        String text = firstPart(payload, "text/plain");
        if (text == null) {
            String html = firstPart(payload, "text/html");
            text = html == null ? "" : stripHtml(html);
        }
        text = trimQuotedReply(text).strip();
        boolean truncated = text.length() > MAX_BODY_CHARS;
        if (truncated) {
            text = text.substring(0, MAX_BODY_CHARS);
        }

        Object internalDate = resource.get("internalDate");
        Instant receivedAt = internalDate == null ? null : Instant.ofEpochMilli(Long.parseLong(internalDate.toString()));
        List<String> labels = new ArrayList<>();
        Object labelIds = resource.get("labelIds");
        if (labelIds instanceof List<?> list) {
            list.forEach(label -> labels.add(String.valueOf(label)));
        }
        return new GmailMessage(String.valueOf(resource.get("id")), header(payload, "Message-ID"), address,
                name.isBlank() ? address : name, header(payload, "Subject"), text, truncated, receivedAt,
                List.copyOf(labels));
    }

    @SuppressWarnings("unchecked")
    private static String header(Map<String, Object> payload, String name) {
        Object headers = payload.get("headers");
        if (headers instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> header = (Map<String, Object>) item;
                if (name.equalsIgnoreCase(String.valueOf(header.get("name")))) {
                    return String.valueOf(header.get("value"));
                }
            }
        }
        return "";
    }

    /** Depth-first search of the MIME tree, skipping attachments. */
    @SuppressWarnings("unchecked")
    private static String firstPart(Map<String, Object> part, String mimeType) {
        String filename = String.valueOf(part.getOrDefault("filename", ""));
        if (mimeType.equalsIgnoreCase(String.valueOf(part.get("mimeType"))) && filename.isEmpty()) {
            Map<String, Object> body = (Map<String, Object>) part.getOrDefault("body", Map.of());
            Object data = body.get("data");
            if (data != null) {
                return new String(Base64.getUrlDecoder().decode(data.toString()), StandardCharsets.UTF_8);
            }
        }
        Object parts = part.get("parts");
        if (parts instanceof List<?> children) {
            for (Object child : children) {
                String found = firstPart((Map<String, Object>) child, mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static String stripHtml(String html) {
        String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        return text.replaceAll("[ \\t]+", " ");
    }

    static String trimQuotedReply(String text) {
        StringBuilder kept = new StringBuilder();
        for (String line : text.split("\\r?\\n", -1)) {
            String trimmed = line.strip();
            if (REPLY_HEADER.matcher(trimmed).matches()) {
                break;
            }
            if (!trimmed.startsWith(">")) {
                kept.append(line).append('\n');
            }
        }
        return kept.toString();
    }
}
