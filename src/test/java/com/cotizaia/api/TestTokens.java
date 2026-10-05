package com.cotizaia.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Supplies agency claims to HTTP tests without bypassing endpoint authorization.
 * Real signature and decoder behavior is exercised separately by AuthControllerTests.
 */
public final class TestTokens {

    private TestTokens() {
    }

    public static RequestPostProcessor forAgency(Long agencyId) {
        return jwt().jwt(token -> token.claim("agencyId", agencyId).subject("1"));
    }
}
