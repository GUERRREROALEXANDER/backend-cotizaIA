package com.cotizaia.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentActorTests {

    private final CurrentActor actor = new CurrentActor();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("agencyId", "1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void theUserIdComesFromTheTokenSubject() {
        signIn("42");

        assertThat(actor.userId()).contains(42L);
    }

    @Test
    void aSubjectThatIsNotANumberGivesNoUser() {
        signIn("owner@demo.co");

        assertThat(actor.userId()).isEmpty();
    }

    @Test
    void withoutAuthenticationThereIsNoUser() {
        assertThat(actor.userId()).isEmpty();
    }

    @Test
    void otherAuthenticationKindsAreIgnored() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "secret"));

        assertThat(actor.userId()).isEmpty();
    }
}
