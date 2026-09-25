package com.example.tickets.comment;

import com.example.tickets.ticket.Ticket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Append-only (OQ-6): there is no update or delete path. */
@Entity
@Table(name = "comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @Column(nullable = false, length = 100, updatable = false)
    private String author;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(Ticket ticket, String author, String body, Instant createdAt) {
        this.ticket = ticket;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
