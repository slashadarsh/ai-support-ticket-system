package com.example.tickets.ai.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tickets.ai.config.RagProperties;
import com.example.tickets.comment.Comment;
import com.example.tickets.ticket.Category;
import com.example.tickets.ticket.Priority;
import com.example.tickets.ticket.Ticket;
import com.example.tickets.ticket.TicketStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class TicketChunkerTest {

    private static final Instant T0 = Instant.parse("2026-09-25T09:00:00Z");
    private final TicketChunker chunker = new TicketChunker(new RagProperties(5, 0.45, new RagProperties.Chunk(400)));

    private static Ticket ticket(String assignee) {
        return new Ticket("TKT-1001", "Card payments failing", "Visa payments are declined.", Priority.HIGH,
                Category.PAYMENT, assignee, T0);
    }

    @Test
    void shouldBuildOneSelfDescribingSummaryChunkWithoutComments() {
        List<Document> docs = chunker.chunk(ticket("Priya Sharma"));

        assertThat(docs).hasSize(1);
        Document summary = docs.get(0);
        assertThat(summary.getText()).isEqualTo("""
                [TKT-1001 | PAYMENT | HIGH | OPEN | assignee: Priya Sharma] Card payments failing
                Description: Visa payments are declined.""");
        assertThat(summary.getMetadata()).containsEntry("ticketId", "TKT-1001").containsEntry("status", "OPEN")
                .containsEntry("priority", "HIGH").containsEntry("category", "PAYMENT")
                .containsEntry("assignee", "Priya Sharma").containsEntry("chunkType", "SUMMARY")
                .containsEntry("chunkIndex", 0).containsEntry("title", "Card payments failing")
                .containsKey("updatedAt");
    }

    @Test
    void shouldIncludeResolutionAndNeverUseNullMetadata() {
        Ticket t = ticket(null);
        t.setResolutionNotes("Rotated the expired API key.");
        t.setStatus(TicketStatus.RESOLVED);

        Document summary = chunker.chunk(t).get(0);

        assertThat(summary.getText()).contains("[TKT-1001 | PAYMENT | HIGH | RESOLVED | assignee: unassigned]")
                .endsWith("Resolution: Rotated the expired API key.");
        assertThat(summary.getMetadata()).containsEntry("assignee", "unassigned").doesNotContainValue(null);
    }

    @Test
    void shouldPackCommentsInOrderAndSplitOnlyAtCommentBoundaries() {
        Ticket t = ticket("Priya Sharma");
        String body = "x".repeat(600); // ~150 tokens per comment line
        for (int i = 0; i < 5; i++) {
            t.getComments().add(new Comment(t, "Agent " + i, i + body, T0.plusSeconds(60L * (5 - i))));
        }

        List<Document> comments = chunker.chunk(t).stream()
                .filter(d -> "COMMENTS".equals(d.getMetadata().get("chunkType"))).toList();

        assertThat(comments).hasSizeGreaterThan(1);
        comments.forEach(d -> {
            assertThat(d.getText()).startsWith("[TKT-1001 | PAYMENT | HIGH | OPEN | assignee: Priya Sharma]");
            assertThat(TicketChunker.estimateTokens(d.getText())).isLessThanOrEqualTo(400);
        });
        String all = String.join("\n", comments.stream().map(Document::getText).toList());
        // Oldest comment (Agent 4) first, and no comment is cut in half.
        assertThat(all.indexOf("Agent 4")).isLessThan(all.indexOf("Agent 0"));
        for (int i = 0; i < 5; i++) {
            assertThat(all).contains("Agent " + i + " (2026-09-25): " + i + body);
        }
    }

    @Test
    void shouldSplitOversizeDescriptionAndRepeatHeaderOnEveryPiece() {
        String longDescription = "The gateway rejects the card. ".repeat(150); // ~4500 chars
        Ticket t = new Ticket("TKT-1002", "Long one", longDescription, Priority.LOW, Category.OTHER, null, T0);

        List<Document> docs = chunker.chunk(t);

        assertThat(docs).hasSizeGreaterThan(1);
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.getText()).startsWith("[TKT-1002 | OTHER | LOW | OPEN | assignee: unassigned] Long one");
            assertThat(d.getMetadata()).containsEntry("chunkType", "SUMMARY");
        });
        assertThat(docs).extracting(d -> d.getMetadata().get("chunkIndex")).containsExactly(
                java.util.stream.IntStream.range(0, docs.size()).boxed().toArray());
    }

    @Test
    void shouldUseDeterministicUuidIds() {
        String first = chunker.chunk(ticket("A")).get(0).getId();
        String second = chunker.chunk(ticket("B")).get(0).getId();

        assertThat(first).isEqualTo(second).isEqualTo(TicketChunker.chunkId("TKT-1001", "SUMMARY", 0));
        assertThat(java.util.UUID.fromString(first)).isNotNull();
    }
}
