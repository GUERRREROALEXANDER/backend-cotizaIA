package com.cotizaia.crypto;

import static com.cotizaia.ingest.PdfFixtures.pdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The security judge for encryption at rest (docs/specs/01-encryption-at-rest.md).
 *
 * <p>A unique marker goes into every piece of agency and client content the app accepts (agency, owner, client,
 * emails, typed brief, PDF brief, question answer), the whole quotation lifecycle runs over HTTP, and then every
 * row of every table is read back over raw JDBC, bypassing JPA. The marker must not appear in any column and no
 * stored file may still carry the {@code %PDF} signature. Through the API the same data must read back as plaintext.
 */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EncryptionAtRestTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void noAgencyOrClientContentIsStoredInPlaintext() throws Exception {
        String marker = "qzx" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String ownerEmail = marker + "@owner.co";

        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia " + marker + "\",\"ownerFullName\":\"Owner " + marker
                                + "\",\"email\":\"" + ownerEmail + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = JsonPath.read(registered.getResponse().getContentAsString(), "$.token");

        long clientId = readLong(call(post("/api/clients"), token, "{\"name\":\"Cliente " + marker
                + "\",\"email\":\"" + marker + "@client.co\",\"company\":\"Empresa " + marker + "\"}",
                status().isCreated()).andReturn(), "$.id");
        long catalogId = readLong(call(post("/api/catalog"), token, "{\"name\":\"Desarrollo web\"}",
                status().isCreated()).andReturn(), "$.id");
        call(post("/api/catalog/" + catalogId + "/requirement-types"), token,
                "{\"name\":\"Sistema de reservas\",\"description\":\"reserva, reservas, agenda\",\"estimatedHours\":32}",
                status().isCreated());
        long roleId = readLong(call(post("/api/roles"), token, "{\"name\":\"Developer\"}", status().isCreated())
                .andReturn(), "$.id");
        call(post("/api/roles/" + roleId + "/rates"), token, "{\"copPerHour\":100000}", status().isCreated());

        String briefText = "Hola soy " + marker + ", tengo un restaurante y necesito reservas en linea";
        MvcResult quoted = call(post("/api/quotations"), token, "{\"source\":\"WEB_FORM\",\"clientId\":" + clientId
                + ",\"payload\":{\"description\":\"" + briefText + "\"}}", status().isCreated()).andReturn();
        long briefId = readLong(quoted, "$.briefId");
        long proposalId = readLong(quoted, "$.proposalId");
        List<Number> questionIds = JsonPath.read(quoted.getResponse().getContentAsString(), "$.questions[*].id");
        assertThat(questionIds).isNotEmpty();
        call(post("/api/briefs/" + briefId + "/questions/" + questionIds.get(0) + "/answer"), token,
                "{\"answer\":\"Respuesta " + marker + "\"}", status().isOk());

        mockMvc.perform(multipart("/api/briefs/upload")
                        .file(new MockMultipartFile("file", "rfp-" + marker + ".pdf", MediaType.APPLICATION_PDF_VALUE,
                                pdf("RFP de " + marker + ": tienda en linea con reservas")))
                        .param("clientId", String.valueOf(clientId)).param("note", "Nota " + marker)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        call(post("/api/proposals/" + proposalId + "/approve"), token, null, status().isOk());
        call(post("/api/proposals/" + proposalId + "/send"), token, null, status().isOk());
        call(post("/api/proposals/" + proposalId + "/accept"), token, null, status().isOk());

        List<String> leaks = scanDatabase(marker);
        assertThat(leaks).as("columns holding plaintext marker or a raw PDF").isEmpty();

        // The API still reads everything back as plaintext.
        call(get("/api/clients/" + clientId), token, null, status().isOk())
                .andExpect(jsonPath("$.name").value("Cliente " + marker))
                .andExpect(jsonPath("$.email").value(marker + "@client.co"));
        call(get("/api/briefs/" + briefId), token, null, status().isOk())
                .andExpect(jsonPath("$.rawText", containsString(marker)));
    }

    @Test
    void loginStillWorksAndEmailsStayUniqueIgnoringCase() throws Exception {
        String email = "Login-" + UUID.randomUUID().toString().substring(0, 8) + "@Demo.co";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia L\",\"ownerFullName\":\"Owner\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toLowerCase(Locale.ROOT) + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia L2\",\"ownerFullName\":\"Owner\",\"email\":\""
                                + email.toUpperCase(Locale.ROOT) + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isConflict());
    }

    /** Returns "table.column" for every column whose raw value contains the marker or a PDF signature. */
    private List<String> scanDatabase(String marker) throws Exception {
        List<String> leaks = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    String table = rs.getString("TABLE_NAME");
                    if ("public".equalsIgnoreCase(schema) && !table.equalsIgnoreCase("flyway_schema_history")) {
                        tables.add(table);
                    }
                }
            }
            assertThat(tables).as("tables found by the scan").contains("briefs", "clients", "users");
            for (String table : tables) {
                try (ResultSet rows = statement.executeQuery("select * from " + table)) {
                    ResultSetMetaData meta = rows.getMetaData();
                    while (rows.next()) {
                        for (int column = 1; column <= meta.getColumnCount(); column++) {
                            String raw = rawValue(rows, column, meta.getColumnType(column));
                            if (raw == null) {
                                continue;
                            }
                            String name = table + "." + meta.getColumnName(column).toLowerCase(Locale.ROOT);
                            if (raw.toLowerCase(Locale.ROOT).contains(marker) || raw.startsWith("%PDF")) {
                                if (!leaks.contains(name)) {
                                    leaks.add(name);
                                }
                            }
                        }
                    }
                }
            }
        }
        return leaks;
    }

    private String rawValue(ResultSet rows, int column, int type) throws Exception {
        return switch (type) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] bytes = rows.getBytes(column);
                yield bytes == null ? null : new String(bytes, StandardCharsets.ISO_8859_1);
            }
            default -> rows.getString(column);
        };
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String json,
            ResultMatcher expected) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andExpect(expected);
    }

    private long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
