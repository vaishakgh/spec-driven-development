# Spec-Driven Development

This repository contains two reference implementations demonstrating spec-driven, AI-agent-assisted software development using structured workflows and human-in-the-loop gates.

---

## Projects

### Brownfield Implementation — YouTube Playlist API Migration

A MuleSoft → Spring Boot migration of an internal YouTube Playlist API service. The existing Mule 4.6.0 service is rewritten as a Spring Boot 4.1.0 / Java 21 service using the **BMad** spec-driven development method, covering analysis, architecture, story creation, implementation, and code review across 7 epics and 19 stories.

[Read more →](./brownfield-implementation/README.md)

---

### Greenfield Implementation — Balance Enquiry API

A Python 3.12 / FastAPI service built from scratch exposing a single authenticated read-only endpoint for payment account balance enquiry. Built using the **Speckit** spec-driven development method, progressing from constitution and specification through planning, task generation, and implementation.

[Read more →](./greenfield-implementation/README.md)
