# API Standards

## General
- Base path `/api`. JSON only. Resource nouns, plural: `/api/tickets`, `/api/tickets/{key}/comments`.
- Tickets are addressed by their business key (`TKT-1001`), not the DB id.
- `camelCase` JSON fields. Timestamps ISO-8601 UTC strings.
- Enums serialised as UPPER_SNAKE strings (`IN_PROGRESS`).

## Methods & status codes
| Action | Method | Success |
|---|---|---|
| Create | POST | 201 + `Location` header + body |
| Read one / list | GET | 200 |
| Partial update | PATCH | 200 |
| Status change | POST `/{key}/transitions` `{ "targetStatus": "..." }` | 200 |
| Add comment | POST `/{key}/comments` | 201 |

- 400 validation error · 404 not found · 409 invalid state transition · 422 not used · 500 unexpected · 503 AI provider unavailable.

## Lists
- `GET /api/tickets?status=OPEN&q=payment&page=0&size=20&sort=createdAt,desc`
- Response: `{ "content": [...], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }` (don't leak Spring's `Page` JSON directly).

## Errors — RFC 7807 ProblemDetail
```json
{
  "type": "https://example.com/problems/invalid-transition",
  "title": "Invalid status transition",
  "status": 409,
  "detail": "Cannot transition TKT-1001 from CLOSED to OPEN",
  "instance": "/api/tickets/TKT-1001/transitions",
  "errors": [ { "field": "title", "message": "must not be blank" } ]
}
```
- `errors` present only for validation failures. Never expose stack traces or SQL.

## AI endpoint
- `POST /api/ai/ask` — contract defined in `spec/rag-api-contract.md`. Always returns 200 with `grounded: false` for no-match; never 404.

## Documentation
- springdoc-openapi at `/swagger-ui.html`. Every endpoint has a summary and documented error responses.
