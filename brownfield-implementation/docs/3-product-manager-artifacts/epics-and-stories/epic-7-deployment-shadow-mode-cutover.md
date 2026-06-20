# Epic 7: Deployment, Shadow Mode & Cutover

The service runs in Kubernetes, shadow mode confirms response parity with Mule, the internal team is notified of the breaking change, traffic switches with zero downtime, and CloudHub is decommissioned.

## Story 7.1: Create Dockerfile and Kubernetes Manifests

As an **operations engineer**,
I want a Dockerfile and Kubernetes manifests for the Spring Boot service,
So that the service can be built into a container image and deployed to Kubernetes with correct resource limits, config, and secret injection.

**Acceptance Criteria:**

**Given** the project Dockerfile
**When** `docker build` is executed
**Then** a runnable container image is produced with Java 21 and the Spring Boot fat JAR

**Given** the container image is running
**When** the container starts with `YOUTUBE_API_KEY` injected as an environment variable
**Then** the service starts successfully and health probes respond at `/actuator/health/liveness` and `/actuator/health/readiness`

**Given** the Kubernetes `Deployment` manifest
**When** applied to a cluster
**Then** the pod runs with CPU request 100m, CPU limit 250m, memory request 256Mi, memory limit 512Mi (NFR-4)
**And** `YOUTUBE_API_KEY` is sourced from a Kubernetes `Secret` — not from a `ConfigMap` or any source-controlled file (NFR-5)
**And** liveness and readiness probes are configured pointing to the Actuator health endpoints

**Given** the Kubernetes `Service` and `ConfigMap` manifests
**When** applied
**Then** the service is reachable within the cluster and environment-specific config (`maxResults`, YouTube base URL) is supplied via ConfigMap rather than hardcoded in the image

## Story 7.2: Implement Shadow Mode Request-Replay Harness

As an **internal developer**,
I want a request-replay harness that sends identical requests to both the Mule and Spring Boot services and compares responses field-by-field,
So that we have objective evidence of response parity before any traffic is switched to the Spring Boot service.

**Acceptance Criteria:**

**Given** both the Mule service (CloudHub) and Spring Boot service (Kubernetes) are running simultaneously
**When** the harness replays a representative set of real requests against both
**Then** responses from both services are compared field-by-field for both endpoints
**And** any field-level discrepancies are reported with the request payload, expected value, and actual value

**Given** the harness completes a full run with zero discrepancies
**When** the results are reviewed
**Then** the shadow mode gate is recorded as PASSED — cutover may proceed

**Given** the harness detects any field-level mismatch
**When** the results are reviewed
**Then** the shadow mode gate is recorded as FAILED — cutover is blocked until the mismatch is resolved
**And** latency of the Spring Boot service is captured during shadow mode runs to verify NFR-1 (≤20% regression vs Mule baseline)

## Story 7.3: Execute Cutover, Consumer Notification, and CloudHub Decommission

As an **internal team lead**,
I want to execute the zero-downtime traffic cutover after shadow mode passes, notify the consumer team of the breaking change, and decommission CloudHub,
So that the migration is complete, costs are reduced, and the consumer team is not surprised by the FR-7 behaviour change.

**Acceptance Criteria:**

**Given** shadow mode gate is PASSED and `mvn test` is green
**When** cutover is initiated
**Then** the team lead sends written notification to the consumer team at least 3 working days before the traffic switch, explicitly documenting the FR-7 breaking change (`GET /api/youtube/song/{videoId}` now returns HTTP 404 instead of HTTP 200 with null fields)

**Given** consumer team confirmation is received
**When** traffic is switched
**Then** the DNS/load balancer entry for the API is updated to point to the Spring Boot Kubernetes service
**And** the Mule CloudHub worker continues running in parallel for a minimum stability observation period before decommission

**Given** the Spring Boot service has operated stably under real traffic
**When** the observation period ends with no incidents
**Then** the CloudHub MICRO worker is stopped and the Anypoint Platform subscription is cancelled
**And** the primary success criterion is confirmed: CloudHub cost eliminated

**Given** all cutover steps are complete
**When** the migration is reviewed
**Then** `GET /actuator/health/readiness` on the Spring Boot service returns HTTP 200 and the Mule CloudHub worker is no longer running
