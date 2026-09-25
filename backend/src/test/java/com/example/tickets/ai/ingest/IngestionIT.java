package com.example.tickets.ai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tickets.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AC-15 and AC-20: chunks are written, and replaced right after every committed change. */
class IngestionIT extends AbstractIntegrationTest {

    private List<String> contents(String key) {
        return jdbc.queryForList(
                "select content from vector_store where metadata::jsonb ->> 'ticketId' = ? order by content", String.class, key);
    }

    private JsonNode metadata(String key, String chunkType) throws Exception {
        String raw = jdbc.queryForObject("select metadata::text from vector_store where metadata::jsonb ->> 'ticketId' = ? "
                + "and metadata::jsonb ->> 'chunkType' = ?", String.class, key, chunkType);
        return json.readTree(raw);
    }

    @Test
    void createStoresChunksWithAllMetadataAndEmbedding() throws Exception {
        String key = createTicket("Card payments failing", "Visa declined at checkout", "HIGH", "PAYMENT");

        assertThat(contents(key)).singleElement().asString()
                .startsWith("[TKT-1001 | PAYMENT | HIGH | OPEN | assignee: unassigned] Card payments failing");
        JsonNode meta = metadata(key, "SUMMARY");
        assertThat(meta.get("ticketId").asText()).isEqualTo(key);
        assertThat(meta.get("status").asText()).isEqualTo("OPEN");
        assertThat(meta.get("priority").asText()).isEqualTo("HIGH");
        assertThat(meta.get("assignee").asText()).isEqualTo("unassigned");
        assertThat(meta.get("category").asText()).isEqualTo("PAYMENT");
        assertThat(jdbc.queryForObject("select vector_dims(embedding) from vector_store", Integer.class)).isEqualTo(1536);
    }

    @Test
    void updateCommentAndTransitionReplaceChunksImmediately() throws Exception {
        String key = createTicket("Old title", "Old description", "LOW", "SHIPPING");

        patchJson("/api/tickets/" + key, Map.of("title", "New title", "assignee", "Meera")).andExpect(status().isOk());
        assertThat(contents(key)).singleElement().asString().contains("New title", "assignee: Meera")
                .doesNotContain("Old title");

        postJson("/api/tickets/" + key + "/comments", Map.of("author", "Ravi", "body", "Carrier webhook failing"))
                .andExpect(status().isCreated());
        assertThat(contents(key)).hasSize(2).anySatisfy(c -> assertThat(c).contains("Ravi", "Carrier webhook failing"));

        transition(key, "IN_PROGRESS").andExpect(status().isOk());
        assertThat(metadata(key, "SUMMARY").get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(metadata(key, "COMMENTS").get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(contents(key)).allSatisfy(c -> assertThat(c).contains("| IN_PROGRESS |").doesNotContain("| OPEN |"));
    }

    @Test
    void failingEmbeddingKeepsOldChunksAndRequestStillSucceeds() throws Exception {
        String key = createTicket("Stable title", "Stable description", "LOW", "OTHER");
        embeddingModel.setFailing(true);

        patchJson("/api/tickets/" + key, Map.of("title", "Changed while AI is down")).andExpect(status().isOk());

        assertThat(contents(key)).singleElement().asString().contains("Stable title");
        embeddingModel.setFailing(false);
        mvc.perform(post("/api/ai/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets").value(1))
                .andExpect(jsonPath("$.failed.length()").value(0));
        assertThat(contents(key)).singleElement().asString().contains("Changed while AI is down");
    }

    @Test
    void rolledBackWriteDoesNotTouchTheVectorStore() throws Exception {
        String key = createTicket("Rollback check");
        List<String> before = contents(key);

        transition(key, "CLOSED").andExpect(status().isConflict());

        assertThat(contents(key)).isEqualTo(before);
    }
}
