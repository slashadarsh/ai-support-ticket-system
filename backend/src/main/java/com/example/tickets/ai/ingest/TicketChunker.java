package com.example.tickets.ai.ingest;

import com.example.tickets.ai.config.RagProperties;
import com.example.tickets.comment.Comment;
import com.example.tickets.ticket.Ticket;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

/**
 * Structure-aware chunking (ADR-1, spec/rag-ingestion.md §2): one SUMMARY chunk (title, description,
 * resolution) plus COMMENTS chunks packed at comment boundaries. Pure: no I/O.
 */
@Component
public class TicketChunker {

    public static final String SUMMARY = "SUMMARY";
    public static final String COMMENTS = "COMMENTS";
    static final int OVERSIZE_TOKENS = 500;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final int maxCommentTokens;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(OVERSIZE_TOKENS)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10_000)
            .withKeepSeparator(true)
            .build();

    public TicketChunker(RagProperties properties) {
        this.maxCommentTokens = properties.chunk().maxTokens();
    }

    public List<Document> chunk(Ticket ticket) {
        String header = header(ticket);
        List<Document> documents = new ArrayList<>();

        StringBuilder summary = new StringBuilder("Description: ").append(ticket.getDescription());
        if (ticket.getResolutionNotes() != null && !ticket.getResolutionNotes().isBlank()) {
            summary.append("\nResolution: ").append(ticket.getResolutionNotes());
        }
        List<String> summaryPieces = splitIfOversize(summary.toString());
        for (int i = 0; i < summaryPieces.size(); i++) {
            documents.add(document(ticket, SUMMARY, i, header + "\n" + summaryPieces.get(i)));
        }

        List<String> packed = packComments(header, ticket.getComments());
        for (int i = 0; i < packed.size(); i++) {
            documents.add(document(ticket, COMMENTS, i, packed.get(i)));
        }
        return documents;
    }

    static String header(Ticket t) {
        String assignee = t.getAssignee() == null ? "unassigned" : t.getAssignee();
        return "[%s | %s | %s | %s | assignee: %s] %s".formatted(
                t.getKey(), t.getCategory(), t.getPriority(), t.getStatus(), assignee, t.getTitle());
    }

    static int estimateTokens(String text) {
        return (text.length() + 3) / 4;
    }

    static String chunkId(String ticketKey, String chunkType, int index) {
        return UUID.nameUUIDFromBytes((ticketKey + "#" + chunkType + "#" + index).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private List<String> packComments(String header, List<Comment> comments) {
        String prefix = header + "\nComments:";
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder(prefix);
        List<Comment> ordered = comments.stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt)
                        .thenComparing(c -> c.getId() == null ? Long.MAX_VALUE : c.getId()))
                .toList();

        for (Comment comment : ordered) {
            String line = "\n- %s (%s): %s".formatted(comment.getAuthor(), DAY.format(comment.getCreatedAt()),
                    comment.getBody());
            if (estimateTokens(line) > OVERSIZE_TOKENS) {
                // A single huge comment gets its own chunk(s); the header is repeated on each piece.
                if (current.length() > prefix.length()) {
                    chunks.add(current.toString());
                    current = new StringBuilder(prefix);
                }
                for (String piece : splitIfOversize(line.strip())) {
                    chunks.add(prefix + "\n" + piece);
                }
                continue;
            }
            if (current.length() > prefix.length() && estimateTokens(current + line) > maxCommentTokens) {
                chunks.add(current.toString());
                current = new StringBuilder(prefix);
            }
            current.append(line);
        }
        if (current.length() > prefix.length()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private List<String> splitIfOversize(String text) {
        if (estimateTokens(text) <= OVERSIZE_TOKENS) {
            return List.of(text);
        }
        return splitter.apply(List.of(new Document(text))).stream().map(Document::getText).toList();
    }

    private static Document document(Ticket t, String chunkType, int index, String text) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ticketId", t.getKey());
        metadata.put("status", t.getStatus().name());
        metadata.put("priority", t.getPriority().name());
        metadata.put("assignee", t.getAssignee() == null ? "unassigned" : t.getAssignee());
        metadata.put("category", t.getCategory().name());
        metadata.put("title", t.getTitle());
        metadata.put("chunkType", chunkType);
        metadata.put("chunkIndex", index);
        metadata.put("updatedAt", t.getUpdatedAt().toString());
        return new Document(chunkId(t.getKey(), chunkType, index), text, metadata);
    }
}
