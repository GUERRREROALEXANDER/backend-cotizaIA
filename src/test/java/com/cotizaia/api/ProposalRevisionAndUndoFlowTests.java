package com.cotizaia.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Spec 06 over HTTP: undo hour edits step by step, and revise a negotiated proposal into a frozen v1 + editable v2. */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProposalRevisionAndUndoFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hourEditsUndoOneStepAtATime() throws Exception {
        Session session = session();
        long proposal = session.quote();
        String base = "/api/proposals/" + proposal;

        session.adjust(proposal, 50);
        session.adjust(proposal, 70);
        session.call(get(base + "/commands")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("ADJUST_HOURS"))
                .andExpect(jsonPath("$[1].undoneAt").doesNotExist());

        session.call(post(base + "/undo")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].hours").value(50));
        session.call(post(base + "/undo")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].hours").value(32));
        session.call(post(base + "/undo")).andExpect(status().isConflict());
        session.call(get(base + "/commands")).andExpect(jsonPath("$[0].undoneAt").isNotEmpty())
                .andExpect(jsonPath("$[1].undoneAt").isNotEmpty());
    }

    @Test
    void negotiatedProposalIsRevisedIntoAFrozenV1AndAnEditableV2() throws Exception {
        Session session = session();
        long v1 = session.quote();
        session.call(post("/api/proposals/" + v1 + "/approve")).andExpect(status().isOk());
        session.call(post("/api/proposals/" + v1 + "/send")).andExpect(status().isOk());
        session.call(post("/api/proposals/" + v1 + "/revise")).andExpect(status().isUnprocessableEntity());
        session.call(post("/api/proposals/" + v1 + "/negotiate")).andExpect(status().isOk());

        MvcResult revised = session.call(post("/api/proposals/" + v1 + "/revise")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.revisionNumber").value(2))
                .andExpect(jsonPath("$.revisionOf").value(v1))
                .andExpect(jsonPath("$.items[0].hours").value(32))
                .andExpect(jsonPath("$.documents.length()").value(3))
                .andReturn();
        Number v2 = JsonPath.read(revised.getResponse().getContentAsString(), "$.id");

        session.adjust(v2.longValue(), 40);
        session.call(get("/api/proposals/" + v1)).andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.revisionNumber").value(1))
                .andExpect(jsonPath("$.items[0].hours").value(32));
        session.call(get("/api/proposals/queue")).andExpect(jsonPath("$[*].id",
                org.hamcrest.Matchers.hasItem(v2.intValue())));

        Session intruder = session();
        intruder.call(post("/api/proposals/" + v2 + "/revise")).andExpect(status().isNotFound());
        intruder.call(post("/api/proposals/" + v2 + "/undo")).andExpect(status().isNotFound());
    }

    private Session session() throws Exception {
        String email = "rev-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia Rev\",\"ownerFullName\":\"Owner\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        Session session = new Session(mockMvc, AuthCookieTests.cookie(registered, "cz_access"));
        session.setUp();
        return session;
    }

    static final class Session {

        private final MockMvc mockMvc;

        private final String token;

        private long clientId;

        Session(MockMvc mockMvc, String token) {
            this.mockMvc = mockMvc;
            this.token = token;
        }

        void setUp() throws Exception {
            clientId = id(call(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Restaurante\"}")).andExpect(status().isCreated()).andReturn());
            long catalogId = id(call(post("/api/catalog").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Web\"}")).andExpect(status().isCreated()).andReturn());
            call(post("/api/catalog/" + catalogId + "/requirement-types").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Sistema de reservas\",\"description\":\"reserva, reservas, agenda\","
                            + "\"estimatedHours\":32}")).andExpect(status().isCreated());
            long roleId = id(call(post("/api/roles").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Developer\"}")).andExpect(status().isCreated()).andReturn());
            call(post("/api/roles/" + roleId + "/rates").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"copPerHour\":100000}")).andExpect(status().isCreated());
        }

        long quote() throws Exception {
            MvcResult quoted = call(post("/api/quotations").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"source\":\"WEB_FORM\",\"clientId\":" + clientId + ",\"payload\":{\"description\":"
                            + "\"Tengo un restaurante y necesito un sistema de reservas en linea.\"}}"))
                    .andExpect(status().isCreated()).andReturn();
            Number proposal = JsonPath.read(quoted.getResponse().getContentAsString(), "$.proposalId");
            return proposal.longValue();
        }

        void adjust(long proposalId, int hours) throws Exception {
            MvcResult detail = call(get("/api/proposals/" + proposalId)).andReturn();
            Number itemId = JsonPath.read(detail.getResponse().getContentAsString(), "$.items[0].id");
            call(put("/api/proposals/" + proposalId + "/items/" + itemId).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"hours\":" + hours + "}")).andExpect(status().isOk());
        }

        ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
            return mockMvc.perform(request.header("Authorization", "Bearer " + token));
        }

        private long id(MvcResult result) throws Exception {
            Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
            return value.longValue();
        }
    }
}
