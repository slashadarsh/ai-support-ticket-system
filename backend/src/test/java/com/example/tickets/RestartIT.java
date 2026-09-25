package com.example.tickets;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tickets.support.TestAiConfig;
import com.example.tickets.ticket.Category;
import com.example.tickets.ticket.Priority;
import com.example.tickets.ticket.TicketDtos.CommentRequest;
import com.example.tickets.ticket.TicketDtos.CreateTicketRequest;
import com.example.tickets.ticket.TicketDtos.TicketResponse;
import com.example.tickets.ticket.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** AC-11: tickets, comments and embeddings survive a full application restart. */
class RestartIT {

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(SupportTicketsApplication.class, TestAiConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run();
    }

    @Test
    void dataSurvivesRestart() {
        String key;
        try (ConfigurableApplicationContext first = start()) {
            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            jdbc.execute("TRUNCATE comment, ticket, vector_store RESTART IDENTITY CASCADE");
            jdbc.execute("ALTER SEQUENCE ticket_key_seq RESTART WITH 1001");
            TicketService service = first.getBean(TicketService.class);
            key = service.create(new CreateTicketRequest("Survives restart", "Persisted in PostgreSQL",
                    Priority.MEDIUM, Category.TECHNICAL, null)).key();
            service.addComment(key, new CommentRequest("Ravi", "Still here after restart"));
        }

        try (ConfigurableApplicationContext second = start()) {
            TicketResponse ticket = second.getBean(TicketService.class).get(key);
            assertThat(ticket.title()).isEqualTo("Survives restart");
            assertThat(ticket.comments()).singleElement().satisfies(c -> assertThat(c.body()).isEqualTo("Still here after restart"));
            Integer chunks = second.getBean(JdbcTemplate.class).queryForObject(
                    "select count(*) from vector_store where metadata::jsonb ->> 'ticketId' = ?", Integer.class, key);
            assertThat(chunks).isEqualTo(2);
        }
    }
}
