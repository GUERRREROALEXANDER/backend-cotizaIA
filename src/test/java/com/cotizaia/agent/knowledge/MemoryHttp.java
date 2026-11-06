package com.cotizaia.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.agent.embedding.EmbeddingClient;
import com.cotizaia.agent.embedding.EmbeddingException;
import com.cotizaia.agent.embedding.EmbeddingPurpose;
import com.cotizaia.api.AuthCookieTests;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Small HTTP driver shared by the learning-loop tests. */
record MemoryHttp(MockMvc mockMvc) {

    record Agency(String token, long clientId) {
    }

    Agency agency() throws Exception {
        String email = "mem-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia Memoria\",\"ownerFullName\":\"Owner\",\"email\":\""
                                + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = AuthCookieTests.cookie(registered, "cz_access");
        long clientId = id(call(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Restaurante\"}"), token).andExpect(status().isCreated()).andReturn());
        long catalogId = id(call(post("/api/catalog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Web\"}"), token).andExpect(status().isCreated()).andReturn());
        call(post("/api/catalog/" + catalogId + "/requirement-types").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sitio web informativo\",\"description\":\"pagina web, sitio, paginas\","
                        + "\"estimatedHours\":30}"), token).andExpect(status().isCreated());
        long roleId = id(call(post("/api/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Developer\"}"), token).andExpect(status().isCreated()).andReturn());
        call(post("/api/roles/" + roleId + "/rates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"copPerHour\":95000}"), token).andExpect(status().isCreated());
        return new Agency(token, clientId);
    }

    long quote(Agency agency, String brief) throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("source", "WEB_FORM").put("clientId", agency.clientId())
                .set("payload", new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                        .put("description", brief)).toString();
        MvcResult quoted = call(post("/api/quotations").contentType(MediaType.APPLICATION_JSON).content(body),
                agency.token()).andExpect(status().isCreated()).andReturn();
        Number proposal = JsonPath.read(quoted.getResponse().getContentAsString(), "$.proposalId");
        return proposal.longValue();
    }

    MvcResult expectItem(Agency agency, long proposalId, String source, int hours) throws Exception {
        return call(get("/api/proposals/" + proposalId), agency.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].requirementType").value("Sitio web informativo"))
                .andExpect(jsonPath("$.items[0].estimateSource").value(source))
                .andExpect(jsonPath("$.items[0].hours").value(hours))
                .andReturn();
    }

    void correctApproveAndSend(Agency agency, long proposalId, int hours) throws Exception {
        MvcResult detail = call(get("/api/proposals/" + proposalId), agency.token()).andReturn();
        Number itemId = JsonPath.read(detail.getResponse().getContentAsString(), "$.items[0].id");
        call(put("/api/proposals/" + proposalId + "/items/" + itemId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\":" + hours + "}"), agency.token()).andExpect(status().isOk());
        call(post("/api/proposals/" + proposalId + "/approve"), agency.token()).andExpect(status().isOk());
        call(post("/api/proposals/" + proposalId + "/send"), agency.token()).andExpect(status().isOk());
    }

    ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private long id(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return value.longValue();
    }
}
