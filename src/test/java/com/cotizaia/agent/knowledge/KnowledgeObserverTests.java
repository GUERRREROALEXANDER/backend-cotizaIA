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

/**
 * Spec 04 learning loop over HTTP: hours a human corrects and sends become memory, and the next similar brief is
 * estimated from that memory with citations.
 */
@SpringBootTest(properties = {"llm.provider=stub", "knowledge.vector-store=jdbc"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KnowledgeObserverTests {

    static final String BRIEF = "Tengo un restaurante en Pasto y necesito una pagina web de 8 paginas con diseño a medida.";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void correctedAndSentHoursBecomeTheNextEstimate() throws Exception {
        MemoryHttp http = new MemoryHttp(mockMvc);
        MemoryHttp.Agency agency = http.agency();

        long first = http.quote(agency, BRIEF);
        http.expectItem(agency, first, "CATALOG", 30);
        http.correctApproveAndSend(agency, first, 77);
        long second = http.quote(agency, BRIEF.replace("8 paginas", "8 páginas y galería"));
        http.correctApproveAndSend(agency, second, 77);

        http.call(get("/api/knowledge/documents"), agency.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].kind").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("APPROVED_PROPOSAL"))))
                .andExpect(jsonPath("$[0].items").value(1));

        long third = http.quote(agency, BRIEF);
        MvcResult detail = http.expectItem(agency, third, "MEMORY", 77);
        List<String> cited = JsonPath.read(detail.getResponse().getContentAsString(),
                "$.items[0].citations[*].documentName");
        assertThat(cited).hasSize(2).allSatisfy(name -> assertThat(name).startsWith("Propuesta #"));
        List<Number> citedHours = JsonPath.read(detail.getResponse().getContentAsString(),
                "$.items[0].citations[*].hours");
        assertThat(citedHours).allSatisfy(hours -> assertThat(hours.intValue()).isEqualTo(77));
    }
}
