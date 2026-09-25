package com.example.tickets.ticket;

import com.example.tickets.comment.Comment;
import com.example.tickets.ticket.TicketDtos.CommentResponse;
import com.example.tickets.ticket.TicketDtos.TicketResponse;
import com.example.tickets.ticket.TicketDtos.TicketSummary;
import java.util.Comparator;

final class TicketMapper {

    static final Comparator<Comment> COMMENT_ORDER =
            Comparator.comparing(Comment::getCreatedAt).thenComparing(Comment::getId);

    private TicketMapper() {
    }

    static TicketResponse toResponse(Ticket t) {
        return new TicketResponse(t.getKey(), t.getTitle(), t.getDescription(), t.getPriority(), t.getCategory(),
                t.getStatus(), t.getAssignee(), t.getResolutionNotes(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getStatus().allowedTransitions(),
                t.getComments().stream().sorted(COMMENT_ORDER).map(TicketMapper::toResponse).toList());
    }

    static TicketSummary toSummary(Ticket t) {
        return new TicketSummary(t.getKey(), t.getTitle(), t.getPriority(), t.getCategory(), t.getStatus(),
                t.getAssignee(), t.getCreatedAt(), t.getUpdatedAt());
    }

    static CommentResponse toResponse(Comment c) {
        return new CommentResponse(c.getId(), c.getAuthor(), c.getBody(), c.getCreatedAt());
    }
}
