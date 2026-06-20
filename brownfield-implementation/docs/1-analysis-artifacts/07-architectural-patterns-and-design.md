# Architectural Patterns and Design

> **Research Note:** Findings drawn from verified training knowledge through August 2025. Reference URLs provided for direct verification.

---

## System Architecture Patterns

The central architectural challenge of this migration is not just replacing a runtime — it is moving from a **centralised ESB/iPaaS topology** to a **distributed, cloud-native microservices topology**. The architecture chosen must handle both states simultaneously during the transition period.

_Strangler Fig Pattern (primary recommendation):_

The Strangler Fig is the consensus architecture for brownfield MuleSoft migrations. Named after the fig tree that grows around and eventually replaces a host tree, it works as follows:

```
Phase 1: Gateway introduced
  Consumer → Spring Cloud Gateway → MuleSoft Runtime (all traffic)

Phase 2: First domain migrated
  Consumer → Spring Cloud Gateway → [MuleSoft Runtime] (80%)
                                 → [Spring Boot: Orders Service] (20%)

Phase 3: Progressive migration
  Consumer → Spring Cloud Gateway → [MuleSoft Runtime] (shrinking)
                                 → [Spring Boot Services] (growing)

Phase N: Migration complete
  Consumer → Spring Cloud Gateway → Spring Boot Services (100%)
                                    MuleSoft Runtime decommissioned
```

Key architectural decisions:
- The **API Gateway** must be introduced before any service migration begins. It becomes the permanent routing layer.
- **Traffic splitting** is controlled at the gateway; consumers see no change.
- **API contracts are frozen** during migration — new Spring Boot services must honour existing RAML/OpenAPI contracts exactly.
- Mule flows are migrated **domain by domain**, not flow by flow.
- Source: https://martinfowler.com/bliki/StranglerFigApplication.html | https://spring.io/projects/spring-cloud-gateway

_Anti-Corruption Layer (ACL) Pattern:_

Where Mule flows interface with complex legacy systems (SAP, mainframes, proprietary protocols), an ACL Spring Boot service acts as a translation adapter:

```
New Spring Service → ACL Adapter (Spring Boot) → MuleSoft flow → Legacy System
```

This defers the hardest connector migrations to later phases while allowing domain services to be built cleanly. The ACL is eventually collapsed once the direct Spring connector is implemented.
Source: https://docs.microsoft.com/en-us/azure/architecture/patterns/anti-corruption-layer

_Domain-Driven Design (DDD) Decomposition:_

MuleSoft flows should be decomposed into **Bounded Contexts** before migration begins. A typical MuleSoft integration project has flows organised by technical concern (connector type, message format) rather than business domain. Re-organisation is required:

| Migration Step | Action |
|---|---|
| 1. Flow inventory | Catalogue all Mule flows: name, trigger, connectors used, DataWeave scripts, error handling |
| 2. Domain mapping | Group flows by business domain (Orders, Customers, Payments, Inventory) |
| 3. Dependency graph | Map inter-flow dependencies (VM connector calls, sub-flows) to identify domain boundaries |
| 4. Seam identification | Find natural seam points where domains exchange data — these become async message boundaries |
| 5. Migrate domain by domain | Start with lowest-dependency, highest-business-value domain |

Source: https://www.oreilly.com/library/view/domain-driven-design/0321125215/

_Hexagonal Architecture (Ports and Adapters):_

Each migrated Spring Boot service should follow hexagonal architecture:

```
[Inbound Adapters]          [Domain Core]          [Outbound Adapters]
HTTP Controller    →→→   Application Service   →→→   JPA Repository
Kafka Listener     →→→   Domain Entities       →→→   REST Client (WebClient)
JMS Listener       →→→   Domain Events         →→→   Kafka Producer
```

This pattern makes the Spring service **connector-agnostic** — replacing an outbound adapter (e.g., upgrading from REST to gRPC) does not touch domain logic. It directly mirrors how Mule separates flow orchestration from connector implementation, but with clean dependency inversion.
Source: https://alistair.cockburn.us/hexagonal-architecture/

---

## Design Principles and Best Practices

_Contract-First API Design:_
- All new Spring Boot APIs **must start with an OpenAPI 3.x specification**, not with code. Use `openapi-generator` to scaffold the controller stubs.
- This mirrors MuleSoft's RAML-first Anypoint Design Center workflow and prevents API contract drift between Mule and Spring implementations during coexistence.
- Enforced via `openapi-generator-maven-plugin` in the build pipeline.
- Source: https://openapi-generator.tech

_SOLID Principles Applied to Migration:_

| SOLID Principle | Migration Application |
|---|---|
| **Single Responsibility** | Each Spring Boot service owns exactly one business domain (not one per Mule connector) |
| **Open/Closed** | New connectors added via new outbound adapter classes, not by modifying domain logic |
| **Liskov Substitution** | Spring service replaces Mule flow without changing consumer contracts |
| **Interface Segregation** | Separate inbound ports (HTTP, Kafka, JMS) from outbound ports (DB, REST, messaging) |
| **Dependency Inversion** | Domain core depends on port interfaces, not on Spring/infrastructure specifics |

_Idempotency:_
- Mule flows often lack explicit idempotency handling; duplicate message processing causes data corruption.
- Spring Boot services must implement idempotency keys at message consumer entry points (Kafka `@KafkaListener` with deduplication, HTTP endpoints with `If-Match` / ETag).
- Source: https://developer.mozilla.org/en-US/docs/Glossary/Idempotent

_Immutable Configuration:_
- Replace Mule's property placeholder files with **Spring Cloud Config Server** (centralised) or **Kubernetes ConfigMaps/Secrets** (cloud-native).
- **12-Factor App** principles applied: config in environment, not in code.
- Source: https://12factor.net/config

---

## Scalability and Performance Patterns

_Horizontal Scaling (replacing CloudHub worker scaling):_
- CloudHub scales by adding Mule worker nodes (vertical/horizontal). In Kubernetes, Spring Boot pods scale horizontally via **Horizontal Pod Autoscaler (HPA)** based on CPU, memory, or custom metrics (Kafka consumer lag via KEDA).
- **KEDA** (Kubernetes Event-Driven Autoscaler) is particularly valuable — it scales Spring Boot Kafka consumers based on topic lag, directly replacing CloudHub's auto-scaling trigger on queue depth.
- Source: https://keda.sh | https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/

_Reactive vs. Synchronous Architecture Decision:_

This is a critical architectural decision point for the migration:

| Scenario | Recommendation |
|---|---|
| Existing Mule flows are synchronous request-response | **Spring MVC** (Tomcat/thread-per-request) — simpler, lower risk |
| Existing Mule flows use non-blocking HTTP / Mule reactive engine | **Spring WebFlux** (Netty/Project Reactor) — preserves non-blocking model |
| High-concurrency, low-latency requirements | **Spring WebFlux** + `WebClient` |
| Java 21 available | **Spring MVC + Virtual Threads** (Project Loom) — near-WebFlux scalability with MVC simplicity |

Recommendation: **Default to Spring MVC + Java 21 Virtual Threads** unless specific reactive pipeline requirements exist. Virtual threads eliminate the scalability gap between MVC and WebFlux without the reactive programming complexity.

Source: https://docs.spring.io/spring-framework/reference/web/webflux.html | https://spring.io/blog/2022/10/11/embracing-virtual-threads

_Caching Architecture:_
- **Spring Cache abstraction** (`@Cacheable`, `@CacheEvict`) with Redis backend replaces MuleSoft's Object Store v2 for caching and idempotency state.
- Multi-level caching: local Caffeine cache (L1) + Redis (L2) for distributed cache consistency across pods.
- Source: https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache

_Bulk Processing Performance:_
- MuleSoft's Batch scope → **Spring Batch** with chunk-oriented processing. Partitioned Spring Batch jobs on Kubernetes replace CloudHub batch workers.
- Chunk size tuning and parallel step execution replace Mule's batch block threading model.
- Source: https://docs.spring.io/spring-batch/docs/current/reference/html/

---

## Security Architecture Patterns

_Zero-Trust Security Model (replacing Anypoint's perimeter security):_

MuleSoft centralises security policy enforcement in Anypoint API Manager (OAuth, rate limiting, IP whitelisting). Migrating to Spring Boot distributes this responsibility:

```
[Consumer] → [Spring Cloud Gateway] → [Spring Boot Service]
               │                         │
               ├─ Rate limiting          ├─ JWT validation (@PreAuthorize)
               ├─ OAuth2 token relay     ├─ Method-level security
               ├─ IP filtering           ├─ Row-level data security
               └─ TLS termination        └─ Audit logging
```

- **Spring Security** with `@EnableMethodSecurity` enforces fine-grained access control within services.
- **Keycloak** or **Auth0** as the OAuth2 / OpenID Connect Authorization Server — replaces Anypoint's built-in OAuth provider.
- Source: https://www.keycloak.org | https://docs.spring.io/spring-security/reference/

_Secrets Management Architecture:_

| Secret Type | MuleSoft | Spring Boot Replacement |
|---|---|---|
| API keys / credentials | Anypoint Secrets Manager | **HashiCorp Vault** via Spring Cloud Vault |
| DB passwords | Secure property placeholders | **Kubernetes Secrets** + sealed-secrets |
| OAuth client secrets | Anypoint OAuth config | **Spring Security OAuth2** client config from Vault |
| Certificates / mTLS | Anypoint trust stores | Kubernetes cert-manager + Istio mTLS |

Source: https://spring.io/projects/spring-vault | https://cert-manager.io

_Audit Logging:_
- Spring AOP `@Aspect` with `@Around` advice intercepts service method calls for audit trails — replacing Mule's Logger component with structured audit logging.
- Output to ELK stack (Elasticsearch + Logstash + Kibana) or cloud-native logging (AWS CloudWatch, Azure Monitor).

---

## Data Architecture Patterns

_Database-Per-Service Pattern:_
- Avoid sharing databases between migrated Spring Boot services (a common anti-pattern when lifting Mule flows that share a schema).
- Each bounded context owns its schema. Cross-domain data access happens via API calls or events — never via shared database joins.
- This enables independent scaling and deployment of each service.
- Source: https://microservices.io/patterns/data/database-per-service.html

_Event-Driven Data Synchronisation:_
- Where MuleSoft flows currently perform synchronous data synchronisation between systems (e.g., Salesforce → database batch sync), replace with **Change Data Capture (CDC)** via Debezium + Kafka.
- Debezium captures database change events and streams them to Kafka topics; Spring Boot Kafka consumers apply changes to target systems asynchronously.
- Source: https://debezium.io | https://spring.io/projects/spring-kafka

_CQRS for High-Read Scenarios:_
- MuleSoft Process APIs that aggregate data from multiple System APIs can be replaced with a **CQRS read model** — a pre-materialised view updated by domain events, served by a dedicated read Spring Boot service.
- Eliminates the fan-out synchronous call pattern common in MuleSoft process flows.

_Schema Management:_
- **Flyway** or **Liquibase** for SQL schema versioning — essential in brownfield migration where existing database schemas are shared with MuleSoft during transition.
- **Confluent Schema Registry** or **Apicurio** for Kafka message schema governance (Avro/Protobuf) — prevents schema drift between producing and consuming services.
- Source: https://flywaydb.org | https://www.apicur.io/registry/

---

## Deployment and Operations Architecture

_Target Kubernetes Topology:_

```
┌─────────────────────────────────────────────────────┐
│  Kubernetes Cluster                                 │
│                                                     │
│  ┌──────────────┐    ┌──────────────────────────┐  │
│  │ Ingress /    │    │  Namespace: integration  │  │
│  │ API Gateway  │───▶│                          │  │
│  │ (Spring      │    │  [Orders Service]        │  │
│  │  Cloud GW /  │    │  [Customers Service]     │  │
│  │  Kong)       │    │  [Payments Service]      │  │
│  └──────────────┘    │  [Notifications Service] │  │
│                      └──────────────────────────┘  │
│  ┌──────────────────────────────────────────────┐  │
│  │  Infrastructure Namespace                    │  │
│  │  [Kafka]  [Redis]  [Vault]  [Config Server]  │  │
│  └──────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────┐  │
│  │  Observability Namespace                     │  │
│  │  [Prometheus]  [Grafana]  [Jaeger]  [ELK]   │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

_GitOps Deployment Model:_
- **ArgoCD** or **Flux** for declarative GitOps — replaces Anypoint Runtime Manager's deployment model.
- All service configuration (Helm charts, Kustomize overlays) stored in Git. Changes to production require a Git commit, not a manual Runtime Manager deployment.
- Source: https://argoproj.github.io/cd/ | https://fluxcd.io

_Container Image Strategy:_
- **Buildpacks** (`spring-boot:build-image` via Cloud Native Buildpacks) for Spring Boot image creation — no Dockerfile required, security-hardened base images.
- Alternatively: **Jib** Maven/Gradle plugin for reproducible, layer-optimised Docker images without a Docker daemon.
- Source: https://buildpacks.io | https://github.com/GoogleContainerTools/jib

_Health and Readiness:_
- **Spring Boot Actuator** provides `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` — mapped directly to Kubernetes `livenessProbe` and `readinessProbe`.
- Replaces CloudHub's worker health monitoring. Custom `HealthIndicator` beans extend health checks to downstream connectors (DB, Kafka, external APIs).
- Source: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html

_Observability Architecture (3 Pillars):_

| Pillar | Tool | Spring Boot Integration |
|---|---|---|
| **Metrics** | Prometheus + Grafana | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` |
| **Logging** | ELK Stack / Loki | Logback + JSON encoder + Logstash appender |
| **Tracing** | Zipkin / Jaeger | `micrometer-tracing` + `micrometer-tracing-bridge-otel` |

- OpenTelemetry (`micrometer-tracing-bridge-otel`) is the emerging standard — provides vendor-neutral trace/metric export.
- Correlation IDs propagated via MDC (Mapped Diagnostic Context) across service boundaries.
- Source: https://micrometer.io/docs/tracing | https://opentelemetry.io

_Mule 3.x vs Mule 4.x Migration Differences:_

| Concern | Mule 3.x | Mule 4.x | Impact on Spring Migration |
|---|---|---|---|
| Runtime model | Synchronous + async | Fully reactive (Project Reactor) | Mule 4 → Spring WebFlux is more natural; Mule 3 → Spring MVC is simpler |
| DataWeave version | DataWeave 1.0 | DataWeave 2.0 | DW 1.0 scripts are more verbose; 2.0 scripts closer to functional Java |
| Error handling | Catch exception strategy | Error Handler (try/error/propagate) | Mule 4 error model maps more cleanly to Spring's exception hierarchy |
| Connectors | Mule 3 DevKit connectors | Mule 4 XML SDK connectors | Mule 3 connectors are not forward-compatible; must re-identify replacements |
| Deployment | On-prem / CloudHub 1.0 | CloudHub 2.0 / RTF (Runtime Fabric) | If on CloudHub 2.0, Kubernetes skills may already exist in the team |

Recommendation: **Identify Mule version before planning**. Mule 3.x migrations carry additional complexity from the 3→4 conceptual gap on top of the 4→Spring gap.
