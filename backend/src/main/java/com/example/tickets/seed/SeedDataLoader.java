package com.example.tickets.seed;

import com.example.tickets.ticket.Category;
import com.example.tickets.ticket.Priority;
import com.example.tickets.ticket.TicketDtos.CommentRequest;
import com.example.tickets.ticket.TicketDtos.CreateTicketRequest;
import com.example.tickets.ticket.TicketDtos.UpdateTicketRequest;
import com.example.tickets.ticket.TicketRepository;
import com.example.tickets.ticket.TicketService;
import com.example.tickets.ticket.TicketStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the golden dataset through the real {@link TicketService}, so seed tickets obey the state machine and
 * are indexed by the normal event path (spec/rag-ingestion.md §5).
 */
@Component
public class SeedDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final TicketService service;
    private final TicketRepository tickets;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public SeedDataLoader(TicketService service, TicketRepository tickets, ObjectMapper objectMapper,
                          @Value("${app.seed.enabled:false}") boolean enabled) {
        this.service = service;
        this.tickets = tickets;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled && tickets.count() == 0) {
            int count = seed();
            log.info("seeded {} tickets", count);
        }
    }

    public int seed() {
        List<SeedTicket> seedTickets = read();
        for (SeedTicket s : seedTickets) {
            String key = service.create(new CreateTicketRequest(s.title(), s.description(), s.priority(),
                    s.category(), s.assignee())).key();
            for (SeedComment c : s.comments()) {
                service.addComment(key, new CommentRequest(c.author(), c.body()));
            }
            if (s.resolutionNotes() != null) {
                service.update(key, new UpdateTicketRequest(null, null, null, null, null, s.resolutionNotes()));
            }
            for (TicketStatus step : pathTo(s.targetStatus())) {
                service.transition(key, step);
            }
        }
        return seedTickets.size();
    }

    static List<TicketStatus> pathTo(TicketStatus target) {
        return switch (target) {
            case OPEN -> List.of();
            case IN_PROGRESS -> List.of(TicketStatus.IN_PROGRESS);
            case RESOLVED -> List.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
            case CLOSED -> List.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED);
            case CANCELLED -> List.of(TicketStatus.CANCELLED);
        };
    }

    private List<SeedTicket> read() {
        try (InputStream in = new ClassPathResource("seed/tickets.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() { });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read seed/tickets.json", e);
        }
    }

    record SeedTicket(String title, String description, Priority priority, Category category, String assignee,
                      TicketStatus targetStatus, String resolutionNotes, List<SeedComment> comments) {
        SeedTicket {
            comments = comments == null ? List.of() : comments;
        }
    }

    record SeedComment(String author, String body) {
    }
}
