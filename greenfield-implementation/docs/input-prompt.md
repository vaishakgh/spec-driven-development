# Input Prompt — Balance Enquiry Feature

This is the original natural-language description that initiated the entire
spec-driven development workflow for this project.

---

I want to create a feature for a Balance Enquiry feature.

**Feature summary** — An authenticated customer can query their own payment account balance
via REST API.

**Endpoint**: `GET /api/accounts/{accountId}/balance`

**Authorization**: customer can only query their own account (`accountId` must match JWT
subject claim); unauthenticated requests are rejected.

**Response payload**: `{ accountId, availableBalance, currency, lastUpdatedAt }` — Read-only,
zero state change, no write side effects.

**Data source**: internal PostgreSQL database (no external service call).

**Error responses**

- `401 Unauthorized` — missing or invalid JWT token
- `403 Forbidden` — account is suspended; balance is readable but access is blocked
- `404 Not Found` — `accountId` does not exist in the database

**Non-functional requirements**

- p99 response time < 500ms under normal load
- Rate limited to 60 requests per minute per authenticated user
- Response must never be cached by the client (`Cache-Control: no-store`)

**Out of scope**

- Admin querying another customer's balance
- Balance history or transaction list
- Multi-currency conversion
