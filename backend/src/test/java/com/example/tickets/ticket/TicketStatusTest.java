package com.example.tickets.ticket;

import static com.example.tickets.ticket.TicketStatus.CANCELLED;
import static com.example.tickets.ticket.TicketStatus.CLOSED;
import static com.example.tickets.ticket.TicketStatus.IN_PROGRESS;
import static com.example.tickets.ticket.TicketStatus.OPEN;
import static com.example.tickets.ticket.TicketStatus.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** All 25 (from, to) pairs of spec/requirements.md §4.1. */
class TicketStatusTest {

    static final Set<List<TicketStatus>> ALLOWED = Set.of(
            List.of(OPEN, IN_PROGRESS),
            List.of(IN_PROGRESS, RESOLVED),
            List.of(RESOLVED, CLOSED),
            List.of(OPEN, CANCELLED),
            List.of(IN_PROGRESS, CANCELLED));

    static Stream<Arguments> allPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                pairs.add(Arguments.of(from, to, ALLOWED.contains(List.of(from, to))));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("allPairs")
    void shouldAllowExactlyTheFiveSpecifiedTransitions(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void shouldHaveTwentyFivePairsWithFiveAllowed() {
        assertThat(allPairs().count()).isEqualTo(25);
        assertThat(allPairs().filter(a -> (boolean) a.get()[2]).count()).isEqualTo(5);
    }

    @Test
    void shouldTreatOnlyClosedAndCancelledAsTerminal() {
        assertThat(Stream.of(TicketStatus.values()).filter(TicketStatus::isTerminal)).containsExactly(CLOSED, CANCELLED);
    }

    @Test
    void shouldExposeAllowedTransitionsInEnumOrder() {
        assertThat(OPEN.allowedTransitions()).containsExactly(IN_PROGRESS, CANCELLED);
        assertThat(IN_PROGRESS.allowedTransitions()).containsExactly(RESOLVED, CANCELLED);
        assertThat(RESOLVED.allowedTransitions()).containsExactly(CLOSED);
        assertThat(CLOSED.allowedTransitions()).isEmpty();
    }
}
