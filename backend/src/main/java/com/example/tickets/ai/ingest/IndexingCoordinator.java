package com.example.tickets.ai.ingest;

import com.example.tickets.ticket.TicketChangedEvent;
import com.example.tickets.ticket.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Decides when to re-index (ADR-4). Kept separate from {@link TicketIndexer} so every call goes through the
 * transactional proxy, and so a failed re-index can be caught here without affecting the user's request.
 */
@Component
public class IndexingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(IndexingCoordinator.class);

    private final TicketIndexer indexer;
    private final TicketRepository tickets;

    public IndexingCoordinator(TicketIndexer indexer, TicketRepository tickets) {
        this.indexer = indexer;
        this.tickets = tickets;
    }

    /** Synchronous, after the ticket write has committed (OQ-12). Never throws. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketChanged(TicketChangedEvent event) {
        try {
            indexer.reindex(event.ticketKey());
        } catch (RuntimeException ex) {
            log.warn("reindex failed ticket={}: {}", event.ticketKey(), ex.toString());
        }
    }

    public ReindexResult reindexAll() {
        List<String> keys = tickets.findAllKeys();
        int chunks = 0;
        List<String> failed = new ArrayList<>();
        for (String key : keys) {
            try {
                chunks += indexer.reindex(key);
            } catch (RuntimeException ex) {
                log.warn("reindex failed ticket={}: {}", key, ex.toString());
                failed.add(key);
            }
        }
        return new ReindexResult(keys.size(), chunks, failed);
    }

    public record ReindexResult(int tickets, int chunks, List<String> failed) {
    }
}
