package com.example.tickets.ticket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tickets.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AC-9, AC-10, AC-14: every (from, to) pair through the HTTP API against a real PostgreSQL. The expected result
 * comes from the spec table in {@link TicketStatusTest}, not from the production enum.
 */
class StateMachineIT extends AbstractIntegrationTest {

    static Stream<Arguments> allPairs() {
        return TicketStatusTest.allPairs();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("allPairs")
    void enforcesTransitionThroughTheApi(TicketStatus from, TicketStatus to, boolean allowed) throws Exception {
        String key = createTicket("state machine " + from + " to " + to);
        patchJson("/api/tickets/" + key, Map.of("resolutionNotes", "Fixed by restarting the service."));
        driveTo(key, from);

        if (allowed) {
            transition(key, to.name()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(to.name()));
            mvc.perform(get("/api/tickets/" + key)).andExpect(jsonPath("$.status").value(to.name()));
        } else {
            transition(key, to.name())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://tickets.example.com/problems/invalid-transition"))
                    .andExpect(jsonPath("$.currentStatus").value(from.name()))
                    .andExpect(jsonPath("$.targetStatus").value(to.name()));
            mvc.perform(get("/api/tickets/" + key)).andExpect(jsonPath("$.status").value(from.name()));
        }
    }

    @Test
    void resolveWithoutResolutionNotesReturns409AndKeepsStatus() throws Exception {
        String key = createTicket("no notes");
        transition(key, "IN_PROGRESS").andExpect(status().isOk());

        transition(key, "RESOLVED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://tickets.example.com/problems/resolution-notes-required"));
        mvc.perform(get("/api/tickets/" + key)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void cannotClearResolutionNotesOfResolvedTicket() throws Exception {
        String key = createTicket("resolved with notes");
        patchJson("/api/tickets/" + key, Map.of("resolutionNotes", "Rotated the key."));
        transition(key, "IN_PROGRESS");
        transition(key, "RESOLVED").andExpect(status().isOk());

        patchJson("/api/tickets/" + key, Map.of("resolutionNotes", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("resolutionNotes"));
    }

    @Test
    void rejectsMissingOrUnknownTargetStatusWith400() throws Exception {
        String key = createTicket("bad target");
        postJson("/api/tickets/" + key + "/transitions", Map.of()).andExpect(status().isBadRequest());
        transition(key, "REOPENED").andExpect(status().isBadRequest());
        transition("TKT-9999", "IN_PROGRESS").andExpect(status().isNotFound());
    }

    private void driveTo(String key, TicketStatus target) throws Exception {
        List<String> path = switch (target) {
            case OPEN -> List.of();
            case IN_PROGRESS -> List.of("IN_PROGRESS");
            case RESOLVED -> List.of("IN_PROGRESS", "RESOLVED");
            case CLOSED -> List.of("IN_PROGRESS", "RESOLVED", "CLOSED");
            case CANCELLED -> List.of("CANCELLED");
        };
        for (String step : path) {
            transition(key, step).andExpect(status().isOk());
        }
    }
}
