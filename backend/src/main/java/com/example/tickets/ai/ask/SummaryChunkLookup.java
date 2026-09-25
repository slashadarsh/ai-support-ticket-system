package com.example.tickets.ai.ask;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Context expansion (ADR-10): similarity search works on chunks, but the LLM must see whole tickets. For every
 * retrieved ticket, its SUMMARY chunk(s) — problem + resolution — are loaded by metadata. Plain SQL on the
 * Flyway-owned {@code vector_store} table: no second embedding call.
 */
@Component
public class SummaryChunkLookup {

    private static final TypeReference<Map<String, Object>> METADATA = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SummaryChunkLookup(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Document> summariesOf(Collection<String> ticketIds) {
        if (ticketIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select id::text, content, metadata::text from vector_store
                where metadata::jsonb ->> 'chunkType' = 'SUMMARY'
                  and metadata::jsonb ->> 'ticketId' = any (?)
                order by metadata::jsonb ->> 'ticketId', (metadata::jsonb ->> 'chunkIndex')::int""",
                (rs, i) -> new Document(rs.getString(1), rs.getString(2), parse(rs.getString(3))),
                (Object) ticketIds.toArray(String[]::new));
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, METADATA);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable chunk metadata", e);
        }
    }
}
