package com.example.tickets.ticket;

import com.example.tickets.comment.Comment;
import com.example.tickets.comment.CommentRepository;
import com.example.tickets.common.ApiExceptions.FieldValidationException;
import com.example.tickets.common.ApiExceptions.FieldViolation;
import com.example.tickets.common.ApiExceptions.InvalidStatusTransitionException;
import com.example.tickets.common.ApiExceptions.ResolutionNotesRequiredException;
import com.example.tickets.common.ApiExceptions.TicketClosedException;
import com.example.tickets.common.ApiExceptions.TicketNotFoundException;
import com.example.tickets.common.PageResponse;
import com.example.tickets.common.Texts;
import com.example.tickets.ticket.TicketDtos.CommentRequest;
import com.example.tickets.ticket.TicketDtos.CommentResponse;
import com.example.tickets.ticket.TicketDtos.CreateTicketRequest;
import com.example.tickets.ticket.TicketDtos.TicketResponse;
import com.example.tickets.ticket.TicketDtos.TicketSummary;
import com.example.tickets.ticket.TicketDtos.UpdateTicketRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final ApplicationEventPublisher events;

    public TicketService(TicketRepository tickets, CommentRepository comments, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.comments = comments;
        this.events = events;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request) {
        String key = "TKT-" + tickets.nextKeyNumber();
        Ticket ticket = new Ticket(key, request.title(), request.description(), request.priority(),
                request.category(), request.assignee(), Texts.now());
        tickets.save(ticket);
        events.publishEvent(new TicketChangedEvent(key));
        return TicketMapper.toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public TicketResponse get(String key) {
        return TicketMapper.toResponse(loadWithComments(key));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketSummary> search(String keyword, TicketStatus status, int page, int size) {
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return PageResponse.of(
                tickets.findAll(TicketRepository.matching(keyword, status), PageRequest.of(page, size, newestFirst)),
                TicketMapper::toSummary);
    }

    @Transactional
    public TicketResponse update(String key, UpdateTicketRequest request) {
        Ticket ticket = loadWithComments(key);
        if (ticket.getStatus().isTerminal()) {
            throw new TicketClosedException(key, ticket.getStatus());
        }
        validatePatch(request, ticket);

        if (request.title() != null) ticket.setTitle(request.title());
        if (request.description() != null) ticket.setDescription(request.description());
        if (request.priority() != null) ticket.setPriority(request.priority());
        if (request.category() != null) ticket.setCategory(request.category());
        if (request.assignee() != null) ticket.setAssignee(Texts.blankToNull(request.assignee()));
        if (request.resolutionNotes() != null) ticket.setResolutionNotes(Texts.blankToNull(request.resolutionNotes()));
        ticket.touch(Texts.now());

        events.publishEvent(new TicketChangedEvent(key));
        return TicketMapper.toResponse(ticket);
    }

    @Transactional
    public TicketResponse transition(String key, TicketStatus target) {
        Ticket ticket = loadWithComments(key);
        TicketStatus current = ticket.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(key, current, target);
        }
        if (target == TicketStatus.RESOLVED && Texts.isBlank(ticket.getResolutionNotes())) {
            throw new ResolutionNotesRequiredException(key);
        }
        ticket.setStatus(target);
        ticket.touch(Texts.now());

        events.publishEvent(new TicketChangedEvent(key));
        return TicketMapper.toResponse(ticket);
    }

    /** Comments are allowed in every status, including terminal ones (OQ-3). */
    @Transactional
    public CommentResponse addComment(String key, CommentRequest request) {
        Ticket ticket = loadWithComments(key);
        Instant now = Texts.now();
        Comment comment = new Comment(ticket, request.author(), request.body(), now);
        ticket.getComments().add(comment);
        comments.save(comment);
        ticket.touch(now);

        events.publishEvent(new TicketChangedEvent(key));
        return TicketMapper.toResponse(comment);
    }

    private Ticket loadWithComments(String key) {
        return tickets.findWithCommentsByKey(key).orElseThrow(() -> new TicketNotFoundException(key));
    }

    private static void validatePatch(UpdateTicketRequest request, Ticket ticket) {
        List<FieldViolation> violations = new ArrayList<>();
        if (request.isEmpty()) {
            violations.add(new FieldViolation("body", "at least one field must be provided"));
        }
        if (request.title() != null && request.title().isEmpty()) {
            violations.add(new FieldViolation("title", "must not be blank"));
        }
        if (request.description() != null && request.description().isEmpty()) {
            violations.add(new FieldViolation("description", "must not be blank"));
        }
        if (request.resolutionNotes() != null && request.resolutionNotes().isEmpty()
                && ticket.getStatus() == TicketStatus.RESOLVED) {
            violations.add(new FieldViolation("resolutionNotes", "cannot be cleared while the ticket is RESOLVED"));
        }
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
    }
}
