package com.cotizaia.ingest.gmail;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Checks a Pub/Sub push token the way Google documents it: RS256 signature
 * against Google's JWKS, issuer accounts.google.com, the audience set on the
 * subscription, and the email claim of the subscription's service account.
 * Without these checks anyone on the internet could ring the doorbell.
 *
 * <p>Built by hand, not as a {@code JwtDecoder} bean: a second JwtDecoder bean
 * would make the app's own resource-server decoder ambiguous.
 */
public class GooglePushTokenVerifier implements PushTokenVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(GooglePushTokenVerifier.class);

    static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";

    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final NimbusJwtDecoder decoder;

    public GooglePushTokenVerifier(String audience, String serviceAccountEmail) {
        if (audience == null || audience.isBlank() || serviceAccountEmail == null || serviceAccountEmail.isBlank()) {
            throw new IllegalStateException(
                    "gmail.push.enabled=true needs GMAIL_PUSH_AUDIENCE and GMAIL_PUSH_SERVICE_ACCOUNT");
        }
        this.decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(),
                claim("iss", jwt -> jwt.getIssuer() != null && GOOGLE_ISSUERS.contains(jwt.getIssuer().toString())),
                claim("aud", jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)),
                claim("email", jwt -> serviceAccountEmail.equalsIgnoreCase(jwt.getClaimAsString("email"))
                        && Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))))));
    }

    @Override
    public boolean verify(String bearerToken) {
        try {
            decoder.decode(bearerToken);
            return true;
        } catch (JwtException e) {
            LOGGER.warn("Rejected Pub/Sub push token: {}", e.getMessage());
            return false;
        }
    }

    private static OAuth2TokenValidator<Jwt> claim(String name, java.util.function.Predicate<Jwt> check) {
        OAuth2Error error = new OAuth2Error("invalid_token", "Pub/Sub push token has a wrong " + name, null);
        return jwt -> check.test(jwt) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(error);
    }
}
