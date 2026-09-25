package com.example.tickets.ticket;

/** Published inside every ticket write transaction; consumed after commit to re-index the ticket (ADR-4). */
public record TicketChangedEvent(String ticketKey) {
}
