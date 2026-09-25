package com.example.tickets.common;

import com.example.tickets.common.ApiExceptions.AiUnavailableException;
import com.example.tickets.common.ApiExceptions.FieldValidationException;
import com.example.tickets.common.ApiExceptions.FieldViolation;
import com.example.tickets.common.ApiExceptions.InvalidStatusTransitionException;
import com.example.tickets.common.ApiExceptions.MalformedRequestException;
import com.example.tickets.common.ApiExceptions.ResolutionNotesRequiredException;
import com.example.tickets.common.ApiExceptions.TicketClosedException;
import com.example.tickets.common.ApiExceptions.TicketNotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps every error to an RFC 7807 ProblemDetail (spec/api-contract.md §3). Never leaks stack traces or SQL. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    static final String TYPE_BASE = "https://tickets.example.com/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), Objects.requireNonNullElse(fe.getDefaultMessage(), "is invalid")))
                .toList();
        return validationProblem(violations, request);
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<ProblemDetail> handleFieldValidation(FieldValidationException ex, HttpServletRequest request) {
        return validationProblem(ex.violations(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException upe) {
            return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                    "Unknown field '" + upe.getPropertyName() + "'", request, Map.of());
        }
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String allowed = Arrays.toString(ife.getTargetType().getEnumConstants());
            return validationProblem(List.of(new FieldViolation(fieldPath(ife), "must be one of " + allowed)), request);
        }
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                "The request body is not valid JSON for this endpoint", request, Map.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MalformedRequestException.class})
    ResponseEntity<ProblemDetail> handleBadParameter(Exception ex, HttpServletRequest request) {
        String detail = ex instanceof MethodArgumentTypeMismatchException mm
                ? "Invalid value '" + mm.getValue() + "' for parameter '" + mm.getName() + "'"
                : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request", detail, request, Map.of());
    }

    @ExceptionHandler(TicketNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(TicketNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "ticket-not-found", "Ticket not found", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidStatusTransitionException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "invalid-transition", "Invalid status transition", ex.getMessage(), request,
                Map.of("currentStatus", ex.current(), "targetStatus", ex.target()));
    }

    @ExceptionHandler(ResolutionNotesRequiredException.class)
    ResponseEntity<ProblemDetail> handleNotesRequired(ResolutionNotesRequiredException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "resolution-notes-required", "Resolution notes required", ex.getMessage(),
                request, Map.of());
    }

    @ExceptionHandler(TicketClosedException.class)
    ResponseEntity<ProblemDetail> handleClosed(TicketClosedException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "ticket-closed", "Ticket can no longer be edited", ex.getMessage(), request,
                Map.of("currentStatus", ex.current()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrentModification(ObjectOptimisticLockingFailureException ex,
                                                               HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification", "Concurrent modification",
                "The ticket was changed by someone else. Reload it and try again.", request, Map.of());
    }

    @ExceptionHandler(AiUnavailableException.class)
    ResponseEntity<ProblemDetail> handleAiUnavailable(AiUnavailableException ex, HttpServletRequest request) {
        log.warn("AI provider unavailable: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().toString());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "ai-unavailable", "AI assistant unavailable",
                "The AI assistant is unavailable right now. Please try again later.", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            // Framework errors (unknown route, wrong method, missing parameter) keep their own status.
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(errorResponse.getBody());
        }
        String correlationId = UUID.randomUUID().toString();
        log.error("Unexpected error [{}] on {} {}", correlationId, request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "Something went wrong. Reference: " + correlationId, request, Map.of());
    }

    private ResponseEntity<ProblemDetail> validationProblem(List<FieldViolation> violations, HttpServletRequest request) {
        List<Map<String, String>> errors = violations.stream()
                .sorted(Comparator.comparing(FieldViolation::field))
                .map(v -> Map.of("field", v.field(), "message", v.message()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed",
                "One or more fields are invalid.", request, Map.of("errors", errors));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String slug, String title, String detail,
                                                        HttpServletRequest request, Map<String, Object> properties) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create(TYPE_BASE + slug));
        body.setTitle(title);
        body.setInstance(URI.create(request.getRequestURI()));
        properties.forEach(body::setProperty);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static String fieldPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .reduce((a, b) -> b.startsWith("[") ? a + b : a + "." + b)
                .orElse("body");
    }
}
