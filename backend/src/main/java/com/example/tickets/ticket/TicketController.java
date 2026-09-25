package com.example.tickets.ticket;

import com.example.tickets.common.ApiExceptions.MalformedRequestException;
import com.example.tickets.common.PageResponse;
import com.example.tickets.common.Texts;
import com.example.tickets.ticket.TicketDtos.CommentRequest;
import com.example.tickets.ticket.TicketDtos.CommentResponse;
import com.example.tickets.ticket.TicketDtos.CreateTicketRequest;
import com.example.tickets.ticket.TicketDtos.TicketResponse;
import com.example.tickets.ticket.TicketDtos.TicketSummary;
import com.example.tickets.ticket.TicketDtos.TransitionRequest;
import com.example.tickets.ticket.TicketDtos.UpdateTicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @Operation(summary = "Create a ticket (status starts as OPEN)")
    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/tickets/" + created.key())).body(created);
    }

    @Operation(summary = "List tickets, newest first; optional keyword search and status filter")
    @GetMapping
    public PageResponse<TicketSummary> list(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) TicketStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        String keyword = Texts.blankToNull(q);
        if (keyword != null && keyword.length() > 100) {
            throw new MalformedRequestException("Parameter 'q' must be at most 100 characters");
        }
        if (page < 0) {
            throw new MalformedRequestException("Parameter 'page' must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new MalformedRequestException("Parameter 'size' must be between 1 and 100");
        }
        return service.search(keyword, status, page, size);
    }

    @Operation(summary = "Get one ticket with its comments")
    @GetMapping("/{key}")
    public TicketResponse get(@PathVariable String key) {
        return service.get(key);
    }

    @Operation(summary = "Update title, description, priority, category, assignee or resolution notes")
    @PatchMapping("/{key}")
    public TicketResponse update(@PathVariable String key, @Valid @RequestBody UpdateTicketRequest request) {
        return service.update(key, request);
    }

    @Operation(summary = "Change status; only transitions allowed by the state machine succeed")
    @PostMapping("/{key}/transitions")
    public TicketResponse transition(@PathVariable String key, @Valid @RequestBody TransitionRequest request) {
        return service.transition(key, request.targetStatus());
    }

    @Operation(summary = "Add a comment")
    @PostMapping("/{key}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable String key, @Valid @RequestBody CommentRequest request) {
        return service.addComment(key, request);
    }
}
