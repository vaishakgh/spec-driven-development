# Deferred Work

## Deferred from: code review of Epic 7 (2026-06-21)

Stories: 7-1, 7-2, 7-3

- **No Kubernetes namespace in manifests** — All k8s manifests (deployment.yaml, service.yaml, configmap.yaml) omit `namespace:`, landing resources in `default`. Should be parameterized or a convention documented per deployment target. [k8s/deployment.yaml, k8s/service.yaml, k8s/configmap.yaml]
- **Single replica with no PodDisruptionBudget** — `replicas: 1` combined with no PDB causes ~15s downtime window during rolling updates. Within the story's spec range (1–2 replicas). Consider PDB and ≥2 replicas when productionizing. [k8s/deployment.yaml]
- **No pod/container securityContext** — No `runAsNonRoot`, `readOnlyRootFilesystem`, `allowPrivilegeEscalation: false`, or dropped capabilities. Pod Security Admission `restricted` policy will reject the pod. Hardening deferred as out of scope for Epic 7. [k8s/deployment.yaml]
- **Memory limit 512Mi mandated by NFR-4** — JVM tuning via `-XX:MaxRAMPercentage=75.0` (patch P9 in story 7-1) mitigates OOMKill risk. Architecture change needed to raise the limit if JVM pressure is observed in production. [k8s/deployment.yaml]
- **`COPY target/*.jar` glob in Dockerfile** — If Maven build ever produces a `-plain.jar` in addition to the fat JAR, the glob will fail. Spring Boot parent BOM defaults suppress the plain JAR; verify pom.xml `spring-boot-maven-plugin` config when upgrading Spring Boot. [Dockerfile]
- **Placeholder IDs in compare.py REQUEST_SCENARIOS** — `PLtest12345` and `dQw4w9WgXcQ` must be replaced with real production playlist/video IDs before the actual shadow mode run. Documented in README. [docs/7-production-ops/compare.py]
- **`IGNORED_FIELDS` set in compare.py has no path-scoping** — Adding any key name to the set silently suppresses that key in all nested objects globally. Currently empty, so no impact. Refactor to path-scoped exclusions if selective field ignoring is needed. [docs/7-production-ops/compare.py]
- **Probe initialDelaySeconds values** — `readinessProbe.initialDelaySeconds: 15` and `livenessProbe.initialDelaySeconds: 30` are spec-mandated. Under CPU pressure, Spring Boot may not complete startup in time, causing crash-loop restarts. Monitor in production and tune if needed. [k8s/deployment.yaml]
