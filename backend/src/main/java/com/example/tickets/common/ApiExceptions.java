package com.example.tickets.common;

import com.example.tickets.ticket.TicketStatus;
import java.util.List;

/** Domain and request exceptions, mapped to ProblemDetail by {@link GlobalExceptionHandler}. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    public record FieldViolation(String field, String message) {
    }

    public static class TicketNotFoundException extends RuntimeException {
        public TicketNotFoundException(String key) {
            super("Ticket " + key + " was not found");
        }
    }

    public static class InvalidStatusTransitionException extends RuntimeException {
        private final TicketStatus current;
        private final TicketStatus target;

        public InvalidStatusTransitionException(String key, TicketStatus current, TicketStatus target) {
            super("Cannot transition " + key + " from " + current + " to " + target);
            this.current = current;
            this.target = target;
        }

        public TicketStatus current() { return current; }
        public TicketStatus target() { return target; }
    }

    public static class ResolutionNotesRequiredException extends RuntimeException {
        public ResolutionNotesRequiredException(String key) {
            super("Add resolution notes to " + key + " before moving it to RESOLVED");
        }
    }

    public static class TicketClosedException extends RuntimeException {
        private final TicketStatus current;

        public TicketClosedException(String key, TicketStatus current) {
            super("Ticket " + key + " is " + current + " and can no longer be edited");
            this.current = current;
        }

        public TicketStatus current() { return current; }
    }

    public static class FieldValidationException extends RuntimeException {
        private final List<FieldViolation> violations;

        public FieldValidationException(List<FieldViolation> violations) {
            super("One or more fields are invalid.");
            this.violations = List.copyOf(violations);
        }

        public List<FieldViolation> violations() { return violations; }
    }

    public static class MalformedRequestException extends RuntimeException {
        public MalformedRequestException(String message) {
            super(message);
        }
    }

    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(Throwable cause) {
            super("The AI provider is unavailable", cause);
        }
    }
}
