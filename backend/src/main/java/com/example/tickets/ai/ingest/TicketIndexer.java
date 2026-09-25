package com.example.tickets.ai.ingest;

import com.example.tickets.ticket.Ticket;
import com.example.tickets.ticket.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Replaces all chunks of one ticket (spec/rag-ingestion.md §3). */
@Component
public class TicketIndexer {

    private static final Logger log = LoggerFactory.getLogger(TicketIndexer.class);

    private final TicketRepository tickets;
    private final TicketChunker chunker;
    private final VectorStore vectorStore;

    public TicketIndexer(TicketRepository tickets, TicketChunker chunker, VectorStore vectorStore) {
        this.tickets = tickets;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
    }

    /**
     * Runs in its own transaction: when called after the ticket's commit, the original transaction is already
     * finished, and writes without a new one would never be committed. If embedding fails, the delete is rolled
     * back with it, so the old chunks stay (stale but present).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reindex(String ticketKey) {
        long started = System.currentTimeMillis();
        Optional<Ticket> ticket = tickets.findWithCommentsByKey(ticketKey);
        if (ticket.isEmpty()) {
            log.warn("reindex skipped, ticket={} not found", ticketKey);
            return 0;
        }
        List<Document> documents = chunker.chunk(ticket.get());
        vectorStore.delete(new FilterExpressionBuilder().eq("ticketId", ticketKey).build());
        vectorStore.add(documents);
        log.info("reindexed ticket={} chunks={} ms={}", ticketKey, documents.size(),
                System.currentTimeMillis() - started);
        return documents.size();
    }
}
