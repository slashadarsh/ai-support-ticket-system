# API Contract — Tickets & Comments
Status: Reviewed
Traces to: FR-1..FR-13, AC-1..AC-13, `.claude/rules/api-standards.md`, `state-machine.md`

AI endpoints are in `rag-api-contract.md`. Base path `/api`, JSON only, UTC ISO-8601 timestamps, enums as UPPER_SNAKE strings. OpenAPI UI: `/swagger-ui.html` (NFR-12).

Unknown JSON fields are rejected (`spring.jackson.deserialization.fail-on-unknown-properties=true`), so a typo or a forbidden field such as `status` in a PATCH is never silently ignored.

## 1. Resources

**TicketResponse** (create, get, patch, transition)
```json
{
  "key": "TKT-1001",
  "title": "Card payments failing at checkout",
  "description": "Customers get 'payment declined' for Visa cards since 09:00.",
  "priority": "HIGH",
  "category": "PAYMENT",
  "status": "IN_PROGRESS",
  "assignee": "Priya Sharma",
  "resolutionNotes": null,
  "createdAt": "2026-09-25T09:12:00Z",
  "updatedAt": "2026-09-25T09:40:00Z",
  "allowedTransitions": ["RESOLVED", "CANCELLED"],
  "comments": [
    { "id": 1, "author": "Priya Sharma", "body": "Gateway returns code 05.", "createdAt": "2026-09-25T09:30:00Z" }
  ]
}
```
`allowedTransitions` comes from `TicketStatus` (state-machine.md §1). Comments are ordered oldest first.

**TicketSummary** (list): `key`, `title`, `priority`, `category`, `status`, `assignee`, `createdAt`, `updatedAt`.

**PageResponse**: `{ "content": [TicketSummary], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }`

## 2. Endpoints

### 2.1 Create ticket — `POST /api/tickets` (FR-1, AC-1)
Request:
```json
{ "title": "Card payments failing at checkout", "description": "…", "priority": "HIGH", "category": "PAYMENT", "assignee": "Priya Sharma" }
```
| Field | Rule |
|---|---|
| `title` | required, not blank, ≤ 200 |
| `description` | required, not blank, ≤ 5000 |
| `priority` | required, `LOW`/`MEDIUM`/`HIGH`/`CRITICAL` |
| `category` | required, `PAYMENT`/`SHIPPING`/`ACCOUNT`/`TECHNICAL`/`OTHER` |
| `assignee` | optional, ≤ 100; null or blank → unassigned |

Status is always `OPEN`; a `status` field in the body is an unknown field → 400.
Response: **201**, `Location: /api/tickets/TKT-1001`, body TicketResponse. Errors: 400.

### 2.2 List / search / filter — `GET /api/tickets` (FR-2, FR-7, FR-8, AC-2, AC-7, AC-8)
| Param | Default | Rule |
|---|---|---|
| `q` | — | optional keyword, trimmed, ≤ 100 chars; blank = no keyword. Case-insensitive substring of `title` or `description` (OQ-7) |
| `status` | — | optional, one status enum value |
| `page` | 0 | ≥ 0 |
| `size` | 20 | 1–100 |

`q` and `status` combine with AND. Sort is fixed: `createdAt` descending. Response **200** PageResponse (empty `content` when nothing matches). Errors: 400 for an invalid `status`, `page` or `size`.

### 2.3 Get ticket — `GET /api/tickets/{key}` (FR-3, AC-3)
**200** TicketResponse. **404** `ticket-not-found` for an unknown key.

### 2.4 Update ticket — `PATCH /api/tickets/{key}` (FR-4, FR-5, AC-4, AC-5)
```json
{ "title": "…", "description": "…", "priority": "CRITICAL", "category": "PAYMENT", "assignee": "", "resolutionNotes": "Rotated the gateway API key." }
```
| Field | Absent or `null` | Value |
|---|---|---|
| `title`, `description` | unchanged | not blank, length rules as create |
| `priority`, `category` | unchanged | enum |
| `assignee` | unchanged | `""` (blank) → **unassign**; otherwise ≤ 100 |
| `resolutionNotes` | unchanged | `""` → clear (rejected with 400 if the ticket is `RESOLVED`); otherwise ≤ 5000 |

- At least one field must be present → otherwise 400.
- `status` is not accepted here (unknown field → 400). Use `/transitions`.
- Ticket in `CLOSED` or `CANCELLED` → **409** `ticket-closed` (OQ-3).

Response **200** TicketResponse. Errors: 400, 404, 409.

### 2.5 Change status — `POST /api/tickets/{key}/transitions` (FR-9, FR-10, AC-9, AC-10)
```json
{ "targetStatus": "RESOLVED" }
```
**200** TicketResponse. Errors: 400 (missing / non-enum `targetStatus`), 404, 409 `invalid-transition`, 409 `resolution-notes-required`, 409 `concurrent-modification`.

### 2.6 Add comment — `POST /api/tickets/{key}/comments` (FR-6, AC-6)
```json
{ "author": "Priya Sharma", "body": "Gateway returns code 05." }
```
`author` required, not blank, ≤ 100. `body` required, not blank, ≤ 2000. Allowed in every status (OQ-3).
**201** body `{ "id": 7, "author": "…", "body": "…", "createdAt": "…" }`. No `Location` header: comments are not individually addressable (they are read through the ticket). Errors: 400, 404.

## 3. Error catalogue (RFC 7807 ProblemDetail)

`type` = `https://tickets.example.com/problems/<slug>`. Never contains stack traces or SQL.

| Slug | Status | When | Extra fields |
|---|---|---|---|
| `validation-error` | 400 | Bean Validation failure, invalid enum value in a body field, PATCH with no fields | `errors: [{ "field", "message" }]` |
| `malformed-request` | 400 | Unparseable JSON, unknown field, invalid query parameter | — |
| `ticket-not-found` | 404 | Unknown ticket key | — |
| `invalid-transition` | 409 | Transition not allowed by the state machine | `currentStatus`, `targetStatus` |
| `resolution-notes-required` | 409 | → RESOLVED with blank resolution notes | — |
| `ticket-closed` | 409 | PATCH on a CLOSED/CANCELLED ticket | `currentStatus` |
| `concurrent-modification` | 409 | Optimistic lock failure | — |
| `ai-unavailable` | 503 | AI provider error on `/api/ai/*` | — |
| `internal-error` | 500 | Anything unexpected (logged with a correlation id) | — |

Example:
```json
{
  "type": "https://tickets.example.com/problems/invalid-transition",
  "title": "Invalid status transition",
  "status": 409,
  "detail": "Cannot transition TKT-1001 from CLOSED to OPEN",
  "instance": "/api/tickets/TKT-1001/transitions",
  "currentStatus": "CLOSED",
  "targetStatus": "OPEN"
}
```
