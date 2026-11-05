package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Spec 02 (docs/specs/02-cookie-auth.md): credentials live only in HttpOnly cookies, cookie-authenticated writes
 * need a CSRF token, header-authenticated API clients don't, and refresh tokens rotate with reuse detection.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthCookieTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registerSetsHttpOnlyStrictCookiesAndReturnsNoToken() throws Exception {
        MvcResult result = register();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(cookie(result, "cz_access"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailOf(result) + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.agencyId").isNumber());
        String access = setCookieHeader(result, "cz_access");
        assertThat(access).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api").contains("Max-Age=900");
        String refresh = setCookieHeader(result, "cz_refresh");
        assertThat(refresh).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/auth");
    }

    @Test
    void accessCookieAuthenticatesReads() throws Exception {
        MvcResult session = register();
        mockMvc.perform(get("/api/clients").cookie(new Cookie("cz_access", cookie(session, "cz_access"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void cookieAuthenticatedWritesNeedTheCsrfToken() throws Exception {
        MvcResult session = register();
        Cookie access = new Cookie("cz_access", cookie(session, "cz_access"));
        mockMvc.perform(post("/api/clients").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sin CSRF\"}"))
                .andExpect(status().isForbidden());

        String csrf = csrfToken();
        mockMvc.perform(post("/api/clients").cookie(access, new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Con CSRF\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void headerAuthenticatedApiClientsNeedNoCsrf() throws Exception {
        MvcResult session = register();
        mockMvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie(session, "cz_access"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"API client\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        MvcResult session = register();
        String first = cookie(session, "cz_refresh");
        MvcResult refreshed = refresh(first).andExpect(status().isOk()).andReturn();
        String second = cookie(refreshed, "cz_refresh");
        assertThat(second).isNotBlank().isNotEqualTo(first);
        assertThat(cookie(refreshed, "cz_access")).isNotBlank();
    }

    @Test
    void replayingARotatedRefreshTokenRevokesTheWholeFamily() throws Exception {
        MvcResult session = register();
        String first = cookie(session, "cz_refresh");
        String second = cookie(refresh(first).andExpect(status().isOk()).andReturn(), "cz_refresh");

        refresh(first).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookiesAndRevokesTheRefreshToken() throws Exception {
        MvcResult session = register();
        String token = cookie(session, "cz_refresh");
        String csrf = csrfToken();
        MvcResult out = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("cz_refresh", token), new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(setCookieHeader(out, "cz_access")).contains("Max-Age=0");
        assertThat(setCookieHeader(out, "cz_refresh")).contains("Max-Age=0");

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokensAreStoredOnlyAsHashes() throws Exception {
        String token = cookie(register(), "cz_refresh");
        List<Map<String, Object>> rows = jdbc.queryForList("select * from refresh_tokens");
        assertThat(rows).isNotEmpty();
        assertThat(rows.toString()).doesNotContain(token);
        assertThat(rows).allSatisfy(row -> assertThat(String.valueOf(row.get("token_hash"))).hasSize(64));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        String csrf = csrfToken();
        return mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("cz_refresh", refreshToken), new Cookie("XSRF-TOKEN", csrf))
                .header("X-XSRF-TOKEN", csrf));
    }

    private String csrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        return cookie(result, "XSRF-TOKEN");
    }

    private MvcResult register() throws Exception {
        String email = "cookie-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia Cookie\",\"ownerFullName\":\"Owner\",\"email\":\""
                                + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();
        result.getRequest().setAttribute("email", email);
        return result;
    }

    private String emailOf(MvcResult result) {
        return (String) result.getRequest().getAttribute("email");
    }

    /** Value of a cookie set by the response, read from the raw Set-Cookie headers. */
    static String cookie(MvcResult result, String name) {
        String header = setCookieHeader(result, name);
        String pair = header.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }

    static String setCookieHeader(MvcResult result, String name) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "="))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("no Set-Cookie for " + name));
    }
}
