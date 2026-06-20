# Performance and Scalability Analysis

## MuleSoft vs Spring Boot Performance Characteristics

MuleSoft Mule 4's reactive runtime (built on Project Reactor) provides non-blocking I/O with efficient thread utilisation for high-concurrency integration workloads. Migrating to Spring Boot requires a deliberate decision on the concurrency model to maintain or improve performance parity.

_Concurrency Model Comparison:_

| Model | MuleSoft Equivalent | Throughput | Latency | Complexity |
|---|---|---|---|---|
| Spring MVC + Java 21 Virtual Threads | Mule 4 reactive runtime | High | Low | Low |
| Spring WebFlux (Project Reactor) | Mule 4 reactive runtime | Very High | Very Low | High |
| Spring MVC (Java 17, thread-per-request) | Mule 3 synchronous | Medium | Medium | Very Low |

**Recommendation:** Enable virtual threads in Spring Boot 3.2+ with `spring.threads.virtual.enabled=true`. Java 21 virtual threads are scheduled by the JVM on carrier threads (not OS threads), enabling millions of concurrent lightweight threads — effectively matching Mule 4's non-blocking model without reactive programming complexity.

Source: https://spring.io/blog/2022/10/11/embracing-virtual-threads | https://openjdk.org/jeps/444

_Connection Pool Tuning:_
- HikariCP (Spring Boot default) is consistently benchmarked as the fastest Java connection pool. Default pool size (`spring.datasource.hikari.maximum-pool-size=10`) is appropriate for low-to-medium load; tune based on database connection limits and concurrency profile.
- Kafka consumer throughput tuned via `max.poll.records`, `fetch.min.bytes`, and consumer group partition assignment — directly analogous to Mule Kafka connector threading configuration.

_Load Testing Approach:_
- **Gatling** or **k6** load tests against each Spring Boot service in staging, with MuleSoft baseline measurements captured first.
- Target: p95 latency within 10% of MuleSoft baseline at equivalent concurrency.
- Spring Boot Actuator `/actuator/metrics` exposes JVM, HTTP, and custom business metrics for comparison.

Source: https://gatling.io | https://k6.io

## Scalability Benchmarks and Patterns

_CloudHub Worker vs Kubernetes Pod Scaling:_

| Scale Dimension | CloudHub | Kubernetes |
|---|---|---|
| Unit of scale | vCore worker (0.1–4 vCores) | Pod (arbitrary CPU/memory) |
| Scale trigger | Manual or scheduled | CPU%, memory%, custom metrics (KEDA) |
| Scale-to-zero | Not supported | Supported (KEDA + Knative) |
| Cross-region | Multiple CloudHub regions | Multi-region K8s clusters |
| Scale speed | Minutes | Seconds (HPA) |

KEDA (Kubernetes Event-Driven Autoscaler) is particularly powerful for Kafka-consumer Spring Boot services: it scales pods based on Kafka consumer lag, ensuring processing capacity always matches message backlog without over-provisioning idle pods.

Source: https://keda.sh/docs/latest/scalers/apache-kafka/

---
