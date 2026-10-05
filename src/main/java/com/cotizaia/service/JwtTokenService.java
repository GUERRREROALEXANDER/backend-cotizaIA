package com.cotizaia.service;

import com.cotizaia.config.SecurityProperties;
import com.cotizaia.domain.AppUser;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed identity and agency claims without HTTP coupling (project.txt section 2).
 * A single issuer prevents registration and login from creating different token contracts.
 */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;

    private final SecurityProperties properties;

    public JwtTokenService(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("agencyId", user.getAgency().getId())
                .claim("role", user.getRole().name())
                .claim("email", user.getLoginEmail())
                .issuer(properties.issuer()).issuedAt(now).expiresAt(expiresAt).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }

    /** Signed token and its expiration for transport-independent callers. */
    public record IssuedToken(String token, Instant expiresAt) {
    }
}
