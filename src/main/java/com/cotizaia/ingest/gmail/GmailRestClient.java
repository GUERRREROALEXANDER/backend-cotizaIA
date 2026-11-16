package com.cotizaia.ingest.gmail;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Gmail REST adapter over plain HTTP: the Google client library would add a
 * large dependency tree to the OSV gate for six GET/POST calls.
 *
 * <p>Auth is an OAuth refresh token (gmail.readonly) exchanged for a
 * short-lived access token, cached until one minute before it expires. It
 * works for a personal Gmail account and for a Workspace mailbox alike.
 */
public class GmailRestClient implements GmailClient {

    static final String API = "https://gmail.googleapis.com/gmail/v1/users/me";

    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;

    private final GmailProperties.OAuth oauth;

    private final Clock clock;

    private String accessToken;

    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public GmailRestClient(RestClient.Builder builder, GmailProperties.OAuth oauth, Clock clock) {
        if (blank(oauth.clientId()) || blank(oauth.clientSecret()) || blank(oauth.refreshToken())) {
            throw new IllegalStateException(
                    "gmail.enabled=true needs GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET and GMAIL_REFRESH_TOKEN");
        }
        this.http = builder.build();
        this.oauth = oauth;
        this.clock = clock;
    }

    @Override
    public String labelId(String labelName) {
        List<Map<String, Object>> labels = list(get(API + "/labels"), "labels");
        return labels.stream()
                .filter(label -> labelName.equalsIgnoreCase(String.valueOf(label.get("name"))))
                .map(label -> String.valueOf(label.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gmail label not found: " + labelName));
    }

    @Override
    public long currentHistoryId() {
        return Long.parseLong(String.valueOf(get(API + "/profile").get("historyId")));
    }

    @Override
    public HistoryPage history(long startHistoryId, String labelId) {
        Set<String> ids = new LinkedHashSet<>();
        long latest = startHistoryId;
        String pageToken = null;
        do {
            Map<String, Object> vars = new java.util.HashMap<>();
            vars.put("start", startHistoryId);
            vars.put("label", labelId);
            vars.put("page", pageToken);
            String template = API + "/history?historyTypes=messageAdded&historyTypes=labelAdded"
                    + "&startHistoryId={start}&labelId={label}" + (pageToken == null ? "" : "&pageToken={page}");
            Map<String, Object> page;
            try {
                page = http.get().uri(template, vars)
                        .header("Authorization", "Bearer " + token())
                        .retrieve().body(JSON);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    throw new HistoryExpiredException("startHistoryId " + startHistoryId + " is no longer available");
                }
                throw e;
            }
            for (Map<String, Object> record : list(page, "history")) {
                collect(record, "messagesAdded", ids);
                collect(record, "labelsAdded", ids);
            }
            if (page.get("historyId") != null) {
                latest = Math.max(latest, Long.parseLong(String.valueOf(page.get("historyId"))));
            }
            pageToken = (String) page.get("nextPageToken");
        } while (pageToken != null);
        return new HistoryPage(new ArrayList<>(ids), latest);
    }

    @Override
    public List<String> search(String query, int max) {
        Map<String, Object> page = http.get()
                .uri(API + "/messages?q={q}&maxResults={max}", query, max)
                .header("Authorization", "Bearer " + token())
                .retrieve().body(JSON);
        return list(page, "messages").stream().map(message -> String.valueOf(message.get("id"))).toList();
    }

    @Override
    public Map<String, Object> message(String id) {
        return http.get().uri(API + "/messages/{id}?format=full", id)
                .header("Authorization", "Bearer " + token())
                .retrieve().body(JSON);
    }

    @Override
    public Watch watch(String topic, String labelId) {
        Map<String, Object> body = Map.of("topicName", topic, "labelIds", List.of(labelId),
                "labelFilterBehavior", "INCLUDE");
        Map<String, Object> response = http.post().uri(API + "/watch")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JSON);
        return new Watch(Long.parseLong(String.valueOf(response.get("historyId"))),
                Instant.ofEpochMilli(Long.parseLong(String.valueOf(response.get("expiration")))));
    }

    private Map<String, Object> get(String url) {
        return http.get().uri(java.net.URI.create(url))
                .header("Authorization", "Bearer " + token())
                .retrieve().body(JSON);
    }

    private synchronized String token() {
        if (accessToken != null && clock.instant().isBefore(accessTokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", oauth.clientId());
        form.add("client_secret", oauth.clientSecret());
        form.add("refresh_token", oauth.refreshToken());
        form.add("grant_type", "refresh_token");
        Map<String, Object> response = http.post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(JSON);
        accessToken = String.valueOf(response.get("access_token"));
        long expiresIn = Long.parseLong(String.valueOf(response.getOrDefault("expires_in", "3600")));
        accessTokenExpiresAt = clock.instant().plusSeconds(expiresIn);
        return accessToken;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Map<String, Object> record, String key, Set<String> ids) {
        for (Map<String, Object> entry : list(record, key)) {
            Map<String, Object> message = (Map<String, Object>) entry.get("message");
            if (message != null && message.get("id") != null) {
                ids.add(String.valueOf(message.get("id")));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value instanceof List<?> items ? (List<Map<String, Object>>) items : List.of();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
