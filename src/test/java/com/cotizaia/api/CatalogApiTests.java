package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** A created requirement type comes back with its id, so the client can edit or delete it without reloading. */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CatalogApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createdRequirementTypeCarriesItsIdAndCanBeEditedAndDeletedWithIt() throws Exception {
        String token = register();
        long catalogId = id(mockMvc.perform(post("/api/catalog").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Desarrollo web\"}"))
                .andExpect(status().isCreated()).andReturn());

        MvcResult created = mockMvc.perform(post("/api/catalog/" + catalogId + "/requirement-types")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Menu digital\",\"description\":\"menu, carta\",\"estimatedHours\":12}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long typeId = id(created);
        assertThat(typeId).isPositive();

        mockMvc.perform(put("/api/catalog/" + catalogId + "/requirement-types/" + typeId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Menu digital\",\"description\":\"menu, carta, qr\",\"estimatedHours\":14}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(typeId))
                .andExpect(jsonPath("$.estimatedHours").value(14));
        mockMvc.perform(delete("/api/catalog/" + catalogId + "/requirement-types/" + typeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Catalogo " + suffix + "\",\"ownerFullName\":\"Owner\","
                                + "\"email\":\"catalog-" + suffix + "@demo.co\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return AuthCookieTests.cookie(registered, "cz_access");
    }

    private static long id(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return value.longValue();
    }
}
