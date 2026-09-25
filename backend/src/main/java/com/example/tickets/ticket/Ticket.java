package com.example.tickets.ticket;

import com.example.tickets.comment.Comment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_key", nullable = false, unique = true, length = 20, updatable = false)
    private String key;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Column(length = 100)
    private String assignee;

    @Column(name = "resolution_notes", length = 5000)
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.PERSIST)
    @OrderBy("createdAt ASC, id ASC")
    private List<Comment> comments = new ArrayList<>();

    protected Ticket() {
    }

    public Ticket(String key, String title, String description, Priority priority, Category category,
                  String assignee, Instant now) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.category = category;
        this.assignee = assignee;
        this.status = TicketStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getKey() { return key; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Priority getPriority() { return priority; }
    public Category getCategory() { return category; }
    public TicketStatus getStatus() { return status; }
    public String getAssignee() { return assignee; }
    public String getResolutionNotes() { return resolutionNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Comment> getComments() { return comments; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public void setCategory(Category category) { this.category = category; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public void touch(Instant now) { this.updatedAt = now; }
}
