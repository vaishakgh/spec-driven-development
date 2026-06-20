# Epic 4: Error Handling

All error conditions across both endpoints return a consistent `{ error, message, code }` ErrorResponse body with semantically correct HTTP status codes, replacing all 11 Mule `on-error-propagate` scopes.

## Story 4.1: Implement ErrorResponse DTO and GlobalExceptionHandler for Request-Level Errors

As an **internal developer**,
I want an `ErrorResponse` DTO and a `GlobalExceptionHandler` (`@ControllerAdvice`) that handles all request-level error conditions,
So that malformed requests, unknown routes, unsupported methods, and unsupported media types all return a consistent `{ error, message, code }` response body.

**Acceptance Criteria:**

**Given** any non-2xx response
**When** the response is returned
**Then** the body contains `error` (string), `message` (string), and `code` (integer matching the HTTP status) (FR-8)

**Given** a GET request to any path other than `/api/youtube/playlists/{playlistId}` and `/api/youtube/song/{videoId}`
**When** the request is received
**Then** HTTP 404 is returned with an `ErrorResponse` body (FR-11)

**Given** a POST, PUT, PATCH, or DELETE request to either defined endpoint path
**When** the request is received
**Then** HTTP 405 is returned with an `ErrorResponse` body (FR-12)

**Given** a request with `Accept: text/html` or any non-`application/json` Accept header
**When** the request is received
**Then** HTTP 406 is returned with an `ErrorResponse` body (FR-13)

**Given** a request with `Content-Type: application/xml`
**When** the request is received
**Then** HTTP 415 is returned with an `ErrorResponse` body (FR-13)

**Given** a request with a missing required path parameter or other spec violation
**When** the request is received
**Then** HTTP 400 is returned with an `ErrorResponse` body (FR-9)

## Story 4.2: Implement Upstream and Unexpected Error Handling

As an **internal developer**,
I want the `GlobalExceptionHandler` to also handle all upstream YouTube API errors and unexpected internal errors,
So that callers receive actionable error responses regardless of where the failure originates.

**Acceptance Criteria:**

**Given** the YouTube API returns HTTP 401 (invalid or missing API key)
**When** either endpoint is called
**Then** HTTP 401 is returned with an `ErrorResponse` where `message: "Invalid or missing YouTube API Key."` and `code: 401` (FR-10)

**Given** a network timeout or connection reset from the YouTube API
**When** either endpoint is called
**Then** HTTP 503 is returned with an `ErrorResponse` where `message: "Unable to connect to YouTube API."` and `code: 503` (FR-14)

**Given** any unhandled exception not covered by other handlers
**When** either endpoint is called
**Then** HTTP 500 is returned with an `ErrorResponse` containing `code: 500` and a generic message (FR-15)
**And** the full exception is logged at ERROR level with sufficient diagnostic context

**Given** all eight error scenarios (FR-8 through FR-15)
**When** any error condition is triggered
**Then** the response body always conforms to the `{ error, message, code }` ErrorResponse schema with no null fields

---
