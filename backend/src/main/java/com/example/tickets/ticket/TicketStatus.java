package com.example.tickets.ticket;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ticket lifecycle. The transition table below is the single source of truth for the state
 * machine (spec/state-machine.md §1); nothing else in the code base may encode transition rules.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED.put(OPEN, Collections.unmodifiableSet(EnumSet.of(IN_PROGRESS, CANCELLED)));
        ALLOWED.put(IN_PROGRESS, Collections.unmodifiableSet(EnumSet.of(RESOLVED, CANCELLED)));
        ALLOWED.put(RESOLVED, Collections.unmodifiableSet(EnumSet.of(CLOSED)));
        ALLOWED.put(CLOSED, Collections.unmodifiableSet(EnumSet.noneOf(TicketStatus.class)));
        ALLOWED.put(CANCELLED, Collections.unmodifiableSet(EnumSet.noneOf(TicketStatus.class)));
    }

    /** Same-status "transitions" are not allowed (OQ-2). */
    public boolean canTransitionTo(TicketStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public List<TicketStatus> allowedTransitions() {
        return List.copyOf(ALLOWED.get(this));
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
