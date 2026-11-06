package com.cotizaia.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Spec 09: briefs and recent agent runs come from the API, newest first, never from browser storage. */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ListEndpointsTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void briefsAreListedNewestFirstAndOnlyForTheCallersAgency() throws Exception {
        String owner = register();
        long clientId = client(owner);
        long first = brief(owner, clientId, "Primer brief: necesito una pagina web");
        long second = brief(owner, clientId, "Segundo brief: necesito una tienda");

        call(get("/api/briefs"), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[0].rawText").value("Segundo brief: necesito una tienda"))
                .andExpect(jsonPath("$[0].clientId").value(clientId));
        call(get("/api/briefs").param("limit", "1"), owner).andExpect(jsonPath("$.length()").value(1));
        call(get("/api/briefs").param("limit", "0"), owner).andExpect(status().isBadRequest());
        call(get("/api/briefs").param("limit", "101"), owner).andExpect(status().isBadRequest());

        String other = register();
        call(get("/api/briefs"), other).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/briefs")).andExpect(status().isUnauthorized());
    }

    @Test
    void latestExecutionsAreListedWithoutABriefId() throws Exception {
        String owner = register();
        long clientId = client(owner);
        long first = brief(owner, clientId, "hola, necesito ayuda con algo");
        call(post("/api/briefs/" + first + "/process"), owner);
        long second = brief(owner, clientId, "hola otra vez, necesito ayuda");
        call(post("/api/briefs/" + second + "/process"), owner);

        call(get("/api/executions"), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].briefId").value(second));
        call(get("/api/executions").param("briefId", String.valueOf(first)), owner)
                .andExpect(jsonPath("$.length()").value(1));
        call(get("/api/executions").param("limit", "51"), owner).andExpect(status().isBadRequest());
        call(get("/api/executions"), register()).andExpect(jsonPath("$.length()").value(0));
    }

    private String register() throws Exception {
        String email = "list-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia List\",\"ownerFullName\":\"Owner\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        return AuthCookieTests.cookie(registered, "cz_access");
    }

    private long client(String token) throws Exception {
        return id(call(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"C\"}"), token)
                .andExpect(status().isCreated()).andReturn());
    }

    private long brief(String token, long clientId, String text) throws Exception {
        return id(call(post("/api/briefs").contentType(MediaType.APPLICATION_JSON).content(
                "{\"source\":\"WEB_FORM\",\"clientId\":" + clientId + ",\"payload\":{\"description\":\"" + text
                        + "\"}}"), token).andExpect(status().isCreated()).andReturn());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private long id(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return value.longValue();
    }
}
