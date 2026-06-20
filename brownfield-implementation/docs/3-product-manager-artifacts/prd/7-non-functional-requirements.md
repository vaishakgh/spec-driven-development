# 7. Non-Functional Requirements

### NFR-1: API response latency

Response latency of the Spring Boot service must not regress more than 20% against the Mule/CloudHub baseline at equivalent request volume, as measured in shadow mode.

### NFR-2: Test coverage parity

All 7 MUnit test scenarios must have functional equivalents in the Spring Boot test suite before cutover is permitted. No regression in test coverage is acceptable.

### NFR-3: Zero-downtime cutover

Traffic must not be fully switched to the Spring Boot service until shadow mode validation has confirmed response parity for all endpoints and all error paths.

### NFR-4: Memory footprint

The production container must operate stably within 512 MB of heap memory, consistent with the current CloudHub MICRO worker allocation.

### NFR-5: No secrets in source control

No API keys, passwords, or other credentials may appear in any source-controlled file. All secrets are injected via environment variables at runtime.

---
