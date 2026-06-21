# Feature Specification: Balance Enquiry

**Feature Branch**: `001-balance-enquiry`

**Created**: 2026-06-21

**Status**: Draft

**Input**: User description: "An authenticated customer can query their own payment account
balance via REST API."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Check My Account Balance (Priority: P1)

An authenticated customer wants to know their current available balance on a payment account.
They call the balance enquiry endpoint with their account ID. The API validates their identity,
confirms they own the account, and returns the current balance along with currency and the
timestamp of the last update.

**Why this priority**: This is the sole function of the feature — the primary and only user
journey. Every other requirement (error handling, rate limiting, caching headers) exists to
support this core flow.

**Independent Test**: A customer with valid credentials and an active account calls
`GET /api/accounts/{accountId}/balance`. The system returns HTTP 200 with a JSON body
containing `accountId`, `availableBalance`, `currency`, and `lastUpdatedAt`.

**Acceptance Scenarios**:

1. **Given** an authenticated customer with a valid JWT token whose `sub` claim matches
   `accountId`, **When** they call `GET /api/accounts/{accountId}/balance`, **Then** the
   system returns HTTP 200 with `{ accountId, availableBalance, currency, lastUpdatedAt }`
   and a `Cache-Control: no-store` response header.

2. **Given** a request with a missing or malformed JWT token, **When** the balance endpoint
   is called, **Then** the system returns HTTP 401 Unauthorized.

3. **Given** an authenticated customer whose JWT `sub` claim does NOT match `accountId`,
   **When** they call `GET /api/accounts/{accountId}/balance`, **Then** the system returns
   HTTP 403 Forbidden.

4. **Given** an authenticated customer whose account is suspended, even if the JWT `sub`
   matches `accountId`, **When** they call the balance endpoint, **Then** the system returns
   HTTP 403 Forbidden.

5. **Given** an authenticated customer whose JWT `sub` matches `accountId`, **When** the
   `accountId` does not exist in the system, **Then** the system returns HTTP 404 Not Found.

6. **Given** an authenticated customer who sends more than 60 requests within a one-minute
   window, **When** subsequent requests are made, **Then** the system returns HTTP 429 Too
   Many Requests with a `Retry-After` header.

---

### Edge Cases

- What happens when the `accountId` path parameter is well-formed but points to an account
  that belongs to a different customer? → HTTP 403 (ownership mismatch takes precedence over
  404 to avoid account enumeration).
- What happens when a valid JWT is provided but the account is simultaneously suspended and
  not owned by the caller? → HTTP 403 (suspension check runs only after ownership is
  confirmed; ownership failure returns 403 first).
- What happens when the customer's rate limit is exactly at 60 requests in the current window?
  → The 60th request succeeds; the 61st returns HTTP 429.
- What happens if `lastUpdatedAt` is null in the database (account exists but was never
  updated)? → Return the field as `null` in the response; do not omit the key.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a read-only endpoint at
  `GET /api/accounts/{accountId}/balance` that returns the payment account balance.
- **FR-002**: The system MUST reject requests that do not carry a valid JWT token with
  HTTP 401.
- **FR-003**: The system MUST reject requests where the authenticated user's identity (JWT
  `sub` claim) does not match the requested `accountId` with HTTP 403.
- **FR-004**: The system MUST reject requests from customers whose account is in a suspended
  state with HTTP 403, even when the JWT and `accountId` ownership are valid.
- **FR-005**: The system MUST return HTTP 404 when the requested `accountId` does not exist
  in the database.
- **FR-006**: The system MUST return a JSON response body containing exactly
  `{ accountId, availableBalance, currency, lastUpdatedAt }` on HTTP 200.
- **FR-007**: The system MUST set `Cache-Control: no-store` on every successful (HTTP 200)
  response.
- **FR-008**: The system MUST enforce a rate limit of 60 requests per minute per authenticated
  user, returning HTTP 429 with a `Retry-After` header when the limit is exceeded.
- **FR-009**: The system MUST source balance data exclusively from the internal payment
  account database; no external service calls are permitted.
- **FR-010**: The system MUST produce zero side effects (no writes, no state mutations) as
  part of a balance read operation.
- **FR-011**: Error responses MUST NOT expose internal implementation details such as stack
  traces, SQL error messages, or internal identifiers.

### Key Entities

- **Payment Account**: Represents a customer's payment account. Key attributes: `accountId`
  (unique identifier), `availableBalance` (decimal), `currency` (ISO 4217 code),
  `lastUpdatedAt` (timestamp), `status` (active / suspended), `ownerSubject` (matches JWT
  `sub` claim).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Customers can retrieve their account balance in under 500ms at the 99th
  percentile under normal operating load.
- **SC-002**: 100% of unauthenticated requests are rejected before any account data is
  accessed.
- **SC-003**: 100% of cross-account access attempts (JWT `sub` ≠ `accountId`) are rejected
  without exposing any account data.
- **SC-004**: The balance response never contains stale data — `availableBalance` and
  `lastUpdatedAt` always reflect the current database record at the time of the request.
- **SC-005**: No balance response is served from a client-side or intermediate cache — all
  clients receive a fresh response on every request.
- **SC-006**: A single customer cannot issue more than 60 balance enquiries per minute;
  attempts beyond this threshold receive a clear, actionable rejection response.

## Assumptions

- The JWT token is issued and validated by an existing authentication service; the balance
  enquiry feature consumes but does not issue tokens.
- The JWT `sub` claim is a stable, unique identifier that maps one-to-one to a customer's
  `accountId`; no aliasing or multiple-account-per-subject scenarios exist in scope.
- Account `status` (active / suspended) is a field already present on the payment account
  record in the database; no external status lookup is required.
- All balance values are stored in a single currency per account; multi-currency conversion
  is explicitly out of scope.
- Admin access to query another customer's balance is out of scope for this feature.
- Balance history and transaction lists are out of scope for this feature.
- The service runs in a single deployment instance for v1; distributed rate limiting across
  replicas is deferred.
- The `availableBalance` field represents the spendable balance (not a pending or total
  balance) as stored in the database.
