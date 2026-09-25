package com.example.tickets.ticket;

import static com.example.tickets.common.Texts.blankToNull;
import static com.example.tickets.common.Texts.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request/response records for the ticket API (spec/api-contract.md). */
public final class TicketDtos {

    private TicketDtos() {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull Priority priority,
            @NotNull Category category,
            @Size(max = 100) String assignee) {

        public CreateTicketRequest {
            title = strip(title);
            description = strip(description);
            assignee = blankToNull(assignee);
        }
    }

    /**
     * PATCH body: absent/null = unchanged. {@code assignee: ""} unassigns, {@code resolutionNotes: ""} clears.
     * There is deliberately no status field; unknown fields are rejected (api-contract.md §2.4).
     */
    public record UpdateTicketRequest(
            @Size(max = 200) String title,
            @Size(max = 5000) String description,
            Priority priority,
            Category category,
            @Size(max = 100) String assignee,
            @Size(max = 5000) String resolutionNotes) {

        public UpdateTicketRequest {
            title = strip(title);
            description = strip(description);
            assignee = strip(assignee);
            resolutionNotes = strip(resolutionNotes);
        }

        boolean isEmpty() {
            return title == null && description == null && priority == null && category == null
                    && assignee == null && resolutionNotes == null;
        }
    }

    public record TransitionRequest(@NotNull TicketStatus targetStatus) {
    }

    public record CommentRequest(
            @NotBlank @Size(max = 100) String author,
            @NotBlank @Size(max = 2000) String body) {

        public CommentRequest {
            author = strip(author);
            body = strip(body);
        }
    }

    public record CommentResponse(Long id, String author, String body, Instant createdAt) {
    }

    public record TicketResponse(
            String key,
            String title,
            String description,
            Priority priority,
            Category category,
            TicketStatus status,
            String assignee,
            String resolutionNotes,
            Instant createdAt,
            Instant updatedAt,
            List<TicketStatus> allowedTransitions,
            List<CommentResponse> comments) {
    }

    public record TicketSummary(
            String key,
            String title,
            Priority priority,
            Category category,
            TicketStatus status,
            String assignee,
            Instant createdAt,
            Instant updatedAt) {
    }
}
