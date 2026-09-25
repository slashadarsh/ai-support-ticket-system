# Java / Spring Boot Guidelines

## Versions
- Java 21 (use records, sealed types, pattern matching, `switch` expressions where they help clarity).
- Spring Boot 3.x, Spring AI 1.x GA. Verify dependency versions against start.spring.io / Maven Central — never invent a version number.

## Package structure (package-by-feature)
```
com.example.tickets
├── ticket/        Ticket entity, TicketStatus, TicketService, TicketController, DTOs
├── comment/       Comment entity, CommentService
├── ai/            ingestion, retrieval, AskController, prompt templates
├── common/        error handling, ProblemDetail advice, config
```

## Layering
- Controller → Service → Repository. Controllers hold no business logic.
- Entities never leave the service layer; controllers take/return DTO records.
- Map with small hand-written mappers (no MapStruct unless needed).

## Domain rules
- Status transitions live in `TicketStatus#canTransitionTo` (single source of truth) and are checked in `TicketService`. Illegal transition → `InvalidStatusTransitionException`.
- Use `@Transactional` on service methods that write. Read methods use `@Transactional(readOnly = true)`.
- Timestamps: `Instant`, UTC, set via `@CreationTimestamp`/`@UpdateTimestamp` or explicitly in service.
- Human-readable ticket key `TKT-<n>` generated from a DB sequence starting at 1001.

## Persistence
- Schema managed by Flyway (`db/migration/V<n>__desc.sql`). `spring.jpa.hibernate.ddl-auto=validate`.
- No `FetchType.EAGER` on collections. Avoid N+1 — use fetch joins or projections for lists.

## Validation & errors
- Bean Validation (`@NotBlank`, `@Size`, `@NotNull`) on request DTOs; `@Valid` in controllers.
- Global `@RestControllerAdvice` returns RFC 7807 `ProblemDetail` (see api-standards.md).

## Config & secrets
- All tunables in `application.yml` bound to `@ConfigurationProperties` records.
- Secrets only via env vars (`${OPENAI_API_KEY}`). `.env` is git-ignored; `.env.example` is committed.

## Style
- Constructor injection only; no field `@Autowired`.
- Use `Optional` for return values, never for fields/params.
- Log with SLF4J, parameterised messages; never log secrets or full prompts containing PII at INFO.
