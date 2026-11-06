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

/** A memory outage (embedding provider down) never blocks quoting or sending; it only skips learning. */
@SpringBootTest(properties = {"llm.provider=stub", "knowledge.vector-store=jdbc"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MemoryOutageTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void quotingAndSendingStillWorkWhenEmbeddingsAreDown() throws Exception {
        MemoryHttp http = new MemoryHttp(mockMvc);
        MemoryHttp.Agency agency = http.agency();
        long proposal = http.quote(agency, KnowledgeObserverTests.BRIEF);
        http.expectItem(agency, proposal, "CATALOG", 30);
        http.correctApproveAndSend(agency, proposal, 50);
        http.call(get("/api/proposals/" + proposal), agency.token())
                .andExpect(jsonPath("$.status").value("SENT"));
        http.call(get("/api/knowledge/documents"), agency.token()).andExpect(jsonPath("$.length()").value(0));
    }

    @TestConfiguration
    static class BrokenEmbeddings {

        @Bean
        @Primary
        EmbeddingClient brokenEmbeddingClient() {
            return new EmbeddingClient() {
                @Override
                public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
                    throw new EmbeddingException("embedding provider down", true);
                }

                @Override
                public String provider() {
                    return "broken";
                }
            };
        }
    }
}
