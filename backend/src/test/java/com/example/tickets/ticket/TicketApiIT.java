package com.example.tickets.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tickets.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AC-1..AC-8 and the PATCH / error contract, through the HTTP API against PostgreSQL. */
class TicketApiIT extends AbstractIntegrationTest {

    @Test
    void createsTicketWithKeyAndOpenStatus() throws Exception {
        postJson("/api/tickets", Map.of("title", "  Card payments failing  ", "description", "Visa declined",
                "priority", "HIGH", "category", "PAYMENT", "assignee", "Priya"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tickets/TKT-1001"))
                .andExpect(jsonPath("$.key").value("TKT-1001"))
                .andExpect(jsonPath("$.title").value("Card payments failing"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.allowedTransitions[1]").value("CANCELLED"));
    }

    @Test
    void rejectsStatusFieldOnCreate() throws Exception {
        postJson("/api/tickets", Map.of("title", "t", "description", "d", "priority", "LOW", "category", "OTHER",
                "status", "CLOSED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://tickets.example.com/problems/malformed-request"))
                .andExpect(jsonPath("$.detail").value("Unknown field 'status'"));
        assertThat(jdbc.queryForObject("select count(*) from ticket", Integer.class)).isZero();
    }

    @Test
    void returnsFieldErrorsForInvalidCreateAndPersistsNothing() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "   ");
        body.put("description", "x".repeat(5001));
        body.put("priority", null);
        body.put("category", "PAYMENT");
        JsonNode problem = read(postJson("/api/tickets", body).andExpect(status().isBadRequest()));

        assertThat(problem.get("type").asText()).endsWith("/validation-error");
        assertThat(problem.get("errors").findValuesAsText("field")).containsExactly("description", "priority", "title");
        assertThat(jdbc.queryForObject("select count(*) from ticket", Integer.class)).isZero();
    }

    @Test
    void reportsInvalidEnumAsFieldError() throws Exception {
        postJson("/api/tickets", Map.of("title", "t", "description", "d", "priority", "URGENT", "category", "OTHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("priority"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of [LOW, MEDIUM, HIGH, CRITICAL]"));
    }

    @Test
    void listsNewestFirstWithPagination() throws Exception {
        createTicket("first");
        createTicket("second");
        createTicket("third");

        mvc.perform(get("/api/tickets?size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].key").value("TKT-1003"))
                .andExpect(jsonPath("$.content[1].key").value("TKT-1002"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/tickets?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets?page=-1")).andExpect(status().isBadRequest());
    }

    @Test
    void returnsDetailWithCommentsAndUnknownKeyReturns404() throws Exception {
        String key = createTicket("with comments");
        postJson("/api/tickets/" + key + "/comments", Map.of("author", "Ravi", "body", "first"));
        postJson("/api/tickets/" + key + "/comments", Map.of("author", "Priya", "body", "second"));

        mvc.perform(get("/api/tickets/" + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(2))
                .andExpect(jsonPath("$.comments[0].body").value("first"))
                .andExpect(jsonPath("$.comments[1].author").value("Priya"));
        mvc.perform(get("/api/tickets/TKT-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://tickets.example.com/problems/ticket-not-found"));
    }

    @Test
    void patchUpdatesOnlyProvidedFields() throws Exception {
        String key = createTicket("old title", "old description", "LOW", "OTHER");

        patchJson("/api/tickets/" + key, Map.of("title", "new title", "priority", "CRITICAL"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tickets/" + key))
                .andExpect(jsonPath("$.title").value("new title"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.description").value("old description"))
                .andExpect(jsonPath("$.category").value("OTHER"));
    }

    @Test
    void patchChangesAndClearsAssignee() throws Exception {
        String key = createTicket("assign me");

        patchJson("/api/tickets/" + key, Map.of("assignee", "Meera")).andExpect(jsonPath("$.assignee").value("Meera"));
        mvc.perform(get("/api/tickets/" + key)).andExpect(jsonPath("$.assignee").value("Meera"));

        patchJson("/api/tickets/" + key, Map.of("assignee", "")).andExpect(status().isOk());
        mvc.perform(get("/api/tickets/" + key)).andExpect(jsonPath("$.assignee").isEmpty());
    }

    @Test
    void patchRejectsEmptyBodyBlankTitleAndStatusField() throws Exception {
        String key = createTicket("patch rules");

        patchJson("/api/tickets/" + key, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        patchJson("/api/tickets/" + key, Map.of("title", "  ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        patchJson("/api/tickets/" + key, Map.of("status", "CLOSED")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'status'"));
        mvc.perform(get("/api/tickets/" + key)).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void patchOnClosedTicketReturns409ButCommentsAreStillAllowed() throws Exception {
        String key = createTicket("to cancel");
        transition(key, "CANCELLED").andExpect(status().isOk());

        patchJson("/api/tickets/" + key, Map.of("title", "changed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://tickets.example.com/problems/ticket-closed"))
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
        postJson("/api/tickets/" + key + "/comments", Map.of("author", "Anita", "body", "note after cancel"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void rejectsInvalidComment() throws Exception {
        String key = createTicket("comment rules");
        postJson("/api/tickets/" + key + "/comments", Map.of("author", "", "body", "x".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        postJson("/api/tickets/TKT-4040/comments", Map.of("author", "a", "body", "b"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchMatchesTitleOrDescriptionCaseInsensitive() throws Exception {
        createTicket("Payment declined", "Visa cards fail", "HIGH", "PAYMENT");
        createTicket("Tracking stuck", "Carrier webhook PAYMENT-free issue", "LOW", "SHIPPING");
        createTicket("Login broken", "Users cannot sign in", "LOW", "ACCOUNT");

        mvc.perform(get("/api/tickets?q=payment"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/tickets?q=VISA"))
                .andExpect(jsonPath("$.content[0].key").value("TKT-1001"));
        mvc.perform(get("/api/tickets?q=nothing-matches-this"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/tickets?q=100%25"))
                .andExpect(jsonPath("$.totalElements").value(0)); // '%' is literal, not a wildcard
    }

    @Test
    void filtersByStatusAndCombinesWithKeyword() throws Exception {
        String a = createTicket("Payment one");
        createTicket("Payment two");
        createTicket("Shipping three");
        transition(a, "IN_PROGRESS");

        mvc.perform(get("/api/tickets?status=IN_PROGRESS"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].key").value(a));
        mvc.perform(get("/api/tickets?status=OPEN&q=payment"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Payment two"));
        mvc.perform(get("/api/tickets?status=DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://tickets.example.com/problems/malformed-request"));
    }
}
