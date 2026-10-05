package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.domain.AppUser;
import com.cotizaia.repository.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises registration, credential privacy and real bearer-token authentication.
 * HTTP and persistence assertions verify the public contract and the stored password representation.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

    private static final String EMAIL = "owner@auth-test.co";

    private static final String PASSWORD = "test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AppUserRepository users;

    @Test
    void registersOwnerAndStoresOnlyHashedPassword() throws Exception {
        MvcResult result = register(EMAIL, PASSWORD);
        String body = result.getResponse().getContentAsString();
        JsonNode response = mapper.readTree(body);
        assertThat(response.path("token").asText()).isNotBlank();
        assertThat(response.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.path("role").asText()).isEqualTo("OWNER");
        assertThat(response.path("expiresAt").asText()).isNotBlank();
        assertThat(body).doesNotContain(PASSWORD, "passwordHash", "password_hash");
        AppUser user = users.findByLoginEmail(EMAIL).orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(user.hasCredentials()).isTrue();
        assertThat(user.getId()).isEqualTo(response.path("userId").asLong());
        assertThat(user.getAgency().getId()).isEqualTo(response.path("agencyId").asLong());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        register(EMAIL, PASSWORD);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("OWNER@AUTH-TEST.CO", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email is already registered"));
    }

    @Test
    void rejectsShortPasswordWithFieldError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(EMAIL, "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.password").isNotEmpty()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("short");
    }

    @Test
    void loginTokenAuthenticatesMeWithRealDecoder() throws Exception {
        JsonNode registered = mapper.readTree(register(EMAIL, PASSWORD).getResponse().getContentAsString());
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("OWNER@AUTH-TEST.CO", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        String body = login.getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD);
        String token = mapper.readTree(body).path("token").asText();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyId").value(registered.path("agencyId").asLong()))
                .andExpect(jsonPath("$.userId").value(registered.path("userId").asLong()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnIdenticalErrors() throws Exception {
        register(EMAIL, PASSWORD);
        for (String email : new String[] {EMAIL, "unknown@auth-test.co"}) {
            MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid email or password")).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("wrong-password");
        }
    }

    @Test
    void masksRequestPasswordsInDiagnosticOutput() {
        assertThat(new RegisterRequest("Agency", "Owner", EMAIL, PASSWORD).toString())
                .contains("password=***").doesNotContain(PASSWORD);
        assertThat(new LoginRequest(EMAIL, PASSWORD).toString())
                .contains("password=***").doesNotContain(PASSWORD);
    }

    @Test
    void rejectsMalformedJsonAndInvalidBearerToken() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON request"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    private MvcResult register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(email, password)))
                .andExpect(status().isCreated()).andReturn();
    }

    private String registrationBody(String email, String password) throws Exception {
        return mapper.writeValueAsString(Map.of("agencyName", "Auth agency", "ownerFullName", "Owner",
                "email", email, "password", password));
    }

    private String loginBody(String email, String password) throws Exception {
        return mapper.writeValueAsString(Map.of("email", email, "password", password));
    }
}
