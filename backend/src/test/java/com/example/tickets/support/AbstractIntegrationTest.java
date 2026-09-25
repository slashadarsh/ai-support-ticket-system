package com.example.tickets.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Full application against the local PostgreSQL database {@code tickets_test} (ADR-9). Deliberately NOT
 * {@code @Transactional}: re-indexing runs after commit, so tests must let transactions commit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAiConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ChatModel chatModel;
    @Autowired protected FakeEmbeddingModel embeddingModel;

    @BeforeEach
    void resetDatabaseAndFakes() {
        jdbc.execute("TRUNCATE comment, ticket, vector_store RESTART IDENTITY CASCADE");
        jdbc.execute("ALTER SEQUENCE ticket_key_seq RESTART WITH 1001");
        Mockito.reset(chatModel);
        embeddingModel.setFailing(false);
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions patchJson(String url, Object body) throws Exception {
        return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Creates a ticket through the API and returns its key. */
    protected String createTicket(String title, String description, String priority, String category) throws Exception {
        return read(postJson("/api/tickets", Map.of("title", title, "description", description,
                "priority", priority, "category", category))).get("key").asText();
    }

    protected String createTicket(String title) throws Exception {
        return createTicket(title, "Description of " + title, "MEDIUM", "OTHER");
    }

    protected ResultActions transition(String key, String target) throws Exception {
        return postJson("/api/tickets/" + key + "/transitions", Map.of("targetStatus", target));
    }
}
