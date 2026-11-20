package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.config.SecurityProperties;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTests {

    private final SecretKey key = new SecretKeySpec(
            "a-test-signing-secret-of-32-bytes!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final SecurityProperties properties = new SecurityProperties("unused", Duration.ofMinutes(15), "cotizaia");
    private final JwtTokenService service = new JwtTokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)),
            properties);
    private final NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256)
            .build();

    private static AppUser owner() {
        Agency agency = new Agency("Estudio Nariño Digital");
        ReflectionTestUtils.setField(agency, "id", 4L);
        AppUser user = new AppUser(agency, "Owner Demo", "owner@demo.co", UserRole.OWNER);
        user.assignCredentials("owner@demo.co", "not-a-real-hash");
        ReflectionTestUtils.setField(user, "id", 9L);
        return user;
    }

    @Test
    void theTokenCarriesTheUserAgencyAndRole() {
        Jwt jwt = decoder.decode(service.issue(owner()).token());

        assertThat(jwt.getSubject()).isEqualTo("9");
        assertThat(((Number) jwt.getClaim("agencyId")).longValue()).isEqualTo(4L);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OWNER");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("owner@demo.co");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("cotizaia");
    }

    @Test
    void theTokenLivesForTheConfiguredTtl() {
        Instant before = Instant.now();
        JwtTokenService.IssuedToken issued = service.issue(owner());
        Jwt jwt = decoder.decode(issued.token());

        // JWT timestamps are whole seconds (RFC 7519 NumericDate).
        assertThat(issued.expiresAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(jwt.getExpiresAt());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwt.getIssuedAt()).isAfterOrEqualTo(before.minusSeconds(1));
    }

    @Test
    void theTokenIsSignedWithHs256() {
        Jwt jwt = decoder.decode(service.issue(owner()).token());

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }
}
