# Epic 5: Observability & API Explorer

Operations can monitor service health for Kubernetes probes; developers can browse the API in Swagger UI in non-prod environments and diagnose issues from structured JSON logs.

## Story 5.1: Configure Swagger UI API Explorer for Non-Production Environments

As an **internal developer**,
I want a browsable Swagger UI available in local and dev profiles,
So that I can explore and manually test the API without writing raw HTTP requests, while ensuring it is not exposed in production.

**Acceptance Criteria:**

**Given** the `local` or `dev` profile is active
**When** a browser navigates to `/swagger-ui.html`
**Then** the Swagger UI loads and displays both API endpoints with all parameters, request shapes, and response schemas (FR-18)
**And** the OAS 3.0 spec is accessible at `/v3/api-docs`

**Given** the `prod` profile is active
**When** a request is made to `/swagger-ui.html` or `/v3/api-docs`
**Then** HTTP 404 is returned — the explorer is not accessible in production (FR-18)

## Story 5.2: Configure Kubernetes Health Probes and Structured JSON Logging

As an **operations engineer**,
I want liveness and readiness health endpoints and structured JSON logs,
So that Kubernetes can probe service health accurately and log aggregation tools can parse and query application logs.

**Acceptance Criteria:**

**Given** the application is running and healthy
**When** `GET /actuator/health/liveness` is called
**Then** HTTP 200 is returned with `{ "status": "UP" }` without requiring authentication (FR-21)

**Given** the application is running and ready to receive traffic
**When** `GET /actuator/health/readiness` is called
**Then** HTTP 200 is returned with `{ "status": "UP" }` without requiring authentication (FR-21)

**Given** an inbound request arrives at either endpoint
**When** the request is processed
**Then** a structured JSON log entry is written at INFO level containing the endpoint called and the key input parameter (`playlistId` or `videoId`) (FR-22)

**Given** a successful response is returned
**When** the response is sent
**Then** a structured JSON log entry is written at INFO level containing the key result metric (`totalResults` count for playlist; video found/not-found for video) (FR-22)

**Given** any error condition occurs
**When** the error is handled
**Then** a structured JSON log entry is written at ERROR level with the exception type, message, and sufficient context to diagnose the failure (FR-22)
**And** all log output is valid JSON — no unstructured or plain-text log lines in any profile

---
