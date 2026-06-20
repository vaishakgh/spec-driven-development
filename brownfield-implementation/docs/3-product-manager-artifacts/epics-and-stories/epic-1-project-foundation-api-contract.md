# Epic 1: Project Foundation & API Contract

The internal team can run the Spring Boot service locally, with the correct API paths recognised and the OAS 3.0 spec established as the contract source of truth. Environment configuration and secret injection work across all profiles.

## Story 1.1: Initialise Spring Boot Project Scaffold

As an **internal developer**,
I want a runnable Spring Boot 3.3.x Maven project with all required dependencies and WebClient configured,
So that I have a working foundation to build the two API endpoints on.

**Acceptance Criteria:**

**Given** a fresh checkout of the repository
**When** `mvn spring-boot:run -Dspring-boot.run.profiles=local` is executed
**Then** the application starts successfully on port 8081 with no errors
**And** `pom.xml` declares Java 21, Spring Boot 3.3.x, `spring-boot-starter-web`, `spring-boot-starter-webflux` (for WebClient), and `springdoc-openapi-starter-webmvc-ui`
**And** a `WebClient` bean is configured with base URL `https://www.googleapis.com/youtube/v3`
**And** no API key value is hardcoded anywhere in source-controlled files

## Story 1.2: Convert RAML 1.0 Spec to OAS 3.0 and Generate Controller Stubs

As an **internal developer**,
I want the API contract defined as an OAS 3.0 specification with `openapi-generator-maven-plugin` generating controller stubs,
So that the implementation is contract-first and the base path is preserved for existing callers.

**Acceptance Criteria:**

**Given** the existing RAML 1.0 spec as input
**When** the OAS 3.0 spec is produced
**Then** it defines both endpoints at `/api/youtube/playlists/{playlistId}` and `/api/youtube/song/{videoId}` (FR-17)
**And** it includes the `pageToken` query parameter on the Playlist endpoint
**And** it includes an HTTP 404 response on the Video endpoint for non-existent video
**And** `thumbnail` fields are marked `nullable: true` in all response schemas
**And** all error response shapes use the `{ error, message, code }` ErrorResponse schema (FR-16)

**Given** the OAS 3.0 spec is present in the project
**When** `mvn generate-sources` is executed
**Then** `openapi-generator-maven-plugin` generates compilable controller interface stubs
**And** the generated stubs return HTTP 501 Not Implemented by default
**And** `mvn compile` succeeds with no errors

## Story 1.3: Configure Multi-Environment Profiles and API Key Injection

As an **internal developer**,
I want distinct Spring Boot configuration profiles for local, dev, production, and test environments with the YouTube API key injected via environment variable,
So that the service behaves correctly in each environment and no secrets appear in source control.

**Acceptance Criteria:**

**Given** `application-local.yml` is active
**When** the service starts
**Then** `maxResults` is set to 25 and the YouTube base URL is `https://www.googleapis.com/youtube/v3`

**Given** `application-prod.yml` is active
**When** the service starts
**Then** `maxResults` is set to 50

**Given** `application-test.yml` is active
**When** the service starts
**Then** a static placeholder API key is used so tests run without a real YouTube connection

**Given** `YOUTUBE_API_KEY` is set in the environment
**When** the service starts on any profile
**Then** the key is injected and no API key value appears in any `.yml`, `.properties`, or any other source-controlled file (FR-20)

**Given** `YOUTUBE_API_KEY` is not set
**When** the service attempts to start
**Then** the application fails to start with a clear configuration error message

---
