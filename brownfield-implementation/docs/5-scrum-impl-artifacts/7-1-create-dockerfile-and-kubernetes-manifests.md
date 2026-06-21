# Story 7.1: Create Dockerfile and Kubernetes Manifests

Status: ready-for-dev

## Story

As an **operations engineer**,
I want a Dockerfile and Kubernetes manifests for the Spring Boot service,
so that the service can be built into a container image and deployed to Kubernetes with correct resource limits, config, and secret injection.

## Acceptance Criteria

1. **Given** the project Dockerfile **When** `docker build` is executed **Then** a runnable container image is produced with Java 21 and the Spring Boot fat JAR
2. **Given** the container image is running **When** the container starts with `YOUTUBE_API_KEY` injected as an environment variable **Then** the service starts successfully and health probes respond at `/actuator/health/liveness` and `/actuator/health/readiness`
3. **Given** the Kubernetes `Deployment` manifest **When** applied to a cluster **Then** the pod runs with CPU request 100m, CPU limit 250m, memory request 256Mi, memory limit 512Mi (NFR-4) **And** `YOUTUBE_API_KEY` is sourced from a Kubernetes `Secret` — not from a `ConfigMap` or source-controlled file (NFR-5) **And** liveness and readiness probes are configured pointing to the Actuator health endpoints
4. **Given** the Kubernetes `Service` and `ConfigMap` manifests **When** applied **Then** the service is reachable within the cluster and environment-specific config (`maxResults`, YouTube base URL) is supplied via ConfigMap

## Tasks / Subtasks

- [ ] Create `Dockerfile` in project root (AC: 1, 2)
  - [ ] Stage 1: `eclipse-temurin:21-jdk-alpine` — build fat JAR with Maven wrapper, `-DskipTests`
  - [ ] Stage 2: `eclipse-temurin:21-jre-alpine` — copy JAR, EXPOSE 8081, ENTRYPOINT
- [ ] Create `.dockerignore` in project root (AC: 1)
  - [ ] Exclude `target/`, `.git/`, `docs/`
- [ ] Create `k8s/` directory and `k8s/deployment.yaml` (AC: 3)
  - [ ] 1–2 replicas
  - [ ] Resource limits: cpu 100m/250m, memory 256Mi/512Mi
  - [ ] `YOUTUBE_API_KEY` from Secret (secretKeyRef)
  - [ ] `SPRING_PROFILES_ACTIVE=dev` (or `prod`) from ConfigMap
  - [ ] Liveness probe: GET /actuator/health/liveness port 8081
  - [ ] Readiness probe: GET /actuator/health/readiness port 8081
- [ ] Create `k8s/service.yaml` (AC: 4)
  - [ ] ClusterIP, port 8081
- [ ] Create `k8s/configmap.yaml` (AC: 4)
  - [ ] `youtube.api.base-url`, `youtube.api.max-results` (NOT `youtube.api.key`)
  - [ ] `SPRING_PROFILES_ACTIVE`

## Dev Notes

### Project Root Structure

The Dockerfile and `.dockerignore` go at the root of the Spring Boot project (next to `pom.xml`):
```
dest-spring-youtube-playlist-api/
├── Dockerfile           ← NEW
├── .dockerignore        ← NEW
├── k8s/
│   ├── deployment.yaml  ← NEW
│   ├── service.yaml     ← NEW
│   └── configmap.yaml   ← NEW
├── pom.xml
├── mvnw
└── src/
```

### Dockerfile — Complete Implementation

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Key decisions:**
- `eclipse-temurin:21-jdk-alpine` (builder): Eclipse Adoptium vendor-neutral JDK 21; Alpine for minimal layer size; matches architecture decision
- `eclipse-temurin:21-jre-alpine` (runtime): JRE-only, excludes compiler and tools; production runtime image is smaller
- `mvnw dependency:go-offline` before copying src: maximises Docker layer caching — dependency downloads cached unless pom.xml changes
- `-DskipTests` in package stage: tests run in CI, not in Docker build
- `COPY .mvn/ .mvn/` + `COPY mvnw pom.xml ./` before `COPY src/ src/`: layering optimization
- No HEALTHCHECK in Dockerfile — health is managed by K8s Deployment probes, not container-level

### .dockerignore — Complete Implementation

```
target/
.git/
.gitignore
docs/
*.md
.mvn/repository/
```

**Why exclude `target/`:** Multi-stage build copies source and builds inside Docker. Pre-built `.class` files or old JARs in `target/` are irrelevant and would bloat the build context.

### k8s/deployment.yaml — Complete Implementation

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: youtube-playlist-api
  labels:
    app: youtube-playlist-api
spec:
  replicas: 1
  selector:
    matchLabels:
      app: youtube-playlist-api
  template:
    metadata:
      labels:
        app: youtube-playlist-api
    spec:
      containers:
        - name: youtube-playlist-api
          image: youtube-playlist-api:latest
          ports:
            - containerPort: 8081
          env:
            - name: SPRING_PROFILES_ACTIVE
              valueFrom:
                configMapKeyRef:
                  name: youtube-playlist-api-config
                  key: SPRING_PROFILES_ACTIVE
            - name: YOUTUBE_API_KEY
              valueFrom:
                secretKeyRef:
                  name: youtube-playlist-api-secret
                  key: YOUTUBE_API_KEY
          resources:
            requests:
              cpu: "100m"
              memory: "256Mi"
            limits:
              cpu: "250m"
              memory: "512Mi"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 15
            periodSeconds: 5
            failureThreshold: 3
```

**Resource limits from NFR-4 (architecture):**
```
requests: cpu 100m, memory 256Mi
limits:   cpu 250m, memory 512Mi
```
These are exact values from `03-core-architectural-decisions.md`. Do not change them.

**Health probe paths from Story 5.2:**
- `/actuator/health/liveness` → Spring Boot `AvailabilityState.CORRECT`
- `/actuator/health/readiness` → Spring Boot `ReadinessState.ACCEPTING_TRAFFIC`
- `initialDelaySeconds: 30` — gives Spring Boot + virtual thread pool time to start (fat JAR with virtual threads)
- `initialDelaySeconds: 15` for readiness — readiness should be available before liveness

**Secret reference — NOT a ConfigMap:**
`YOUTUBE_API_KEY` MUST use `secretKeyRef`. Using `configMapKeyRef` for secrets violates NFR-5 and Kubernetes security practices. The Secret itself is NOT created by this story — ops creates it manually before deployment:
```bash
kubectl create secret generic youtube-playlist-api-secret \
  --from-literal=YOUTUBE_API_KEY=<actual-key>
```

### k8s/service.yaml — Complete Implementation

```yaml
apiVersion: v1
kind: Service
metadata:
  name: youtube-playlist-api
spec:
  selector:
    app: youtube-playlist-api
  ports:
    - protocol: TCP
      port: 8081
      targetPort: 8081
  type: ClusterIP
```

ClusterIP: internal cluster access only. No `LoadBalancer` or `NodePort` — ingress/TLS is handled at the cluster ingress layer, not in this manifest.

### k8s/configmap.yaml — Complete Implementation

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: youtube-playlist-api-config
data:
  SPRING_PROFILES_ACTIVE: "dev"
  YOUTUBE_API_BASE_URL: "https://www.googleapis.com/youtube/v3"
  YOUTUBE_API_MAX_RESULTS: "25"
```

**What goes in ConfigMap vs Secret:**
| Config | Where | Reason |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ConfigMap | Not sensitive |
| `YOUTUBE_API_BASE_URL` | ConfigMap | Not sensitive |
| `YOUTUBE_API_MAX_RESULTS` | ConfigMap | Not sensitive |
| `YOUTUBE_API_KEY` | Secret | Sensitive credential — never ConfigMap |

Note: The Spring Boot application reads `youtube.api.base-url` (kebab-case YAML property). When injected as `YOUTUBE_API_BASE_URL` env var, Spring Boot's relaxed binding resolves `YOUTUBE_API_BASE_URL` → `youtube.api.base-url`. This is Spring Boot's standard env var binding.

### Build and Run Commands

```bash
# Build the container image
docker build -t youtube-playlist-api:latest .

# Run locally (requires YOUTUBE_API_KEY in env)
docker run -p 8081:8081 \
  -e YOUTUBE_API_KEY=$YOUTUBE_API_KEY \
  -e SPRING_PROFILES_ACTIVE=local \
  youtube-playlist-api:latest

# Deploy to Kubernetes
kubectl apply -f k8s/
```

### Architecture Constraints

- Base image: `eclipse-temurin:21-jre-alpine` (runtime stage) — vendor-neutral, Alpine-based, JRE-only [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Containerisation]
- Resource limits exact values: NFR-4 [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Resource limits]
- `YOUTUBE_API_KEY` from K8s Secret only — never ConfigMap, never source-controlled [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Authentication & Security]

### No Test Changes in This Story

This story creates infrastructure files only. No test classes are added or modified.

### Project Structure Notes

- NEW: `Dockerfile`
- NEW: `.dockerignore`
- NEW: `k8s/deployment.yaml`
- NEW: `k8s/service.yaml`
- NEW: `k8s/configmap.yaml`
- All files created relative to `dest-spring-youtube-playlist-api/` root
- No Java source code changes

### References

- [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-7-deployment-shadow-mode-cutover.md#Story 7.1]
- [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Containerisation]
- [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Kubernetes manifest set]
- [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Resource limits (NFR-4 compliance)]
- [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Complete Project Directory Structure]
- eclipse-temurin Docker Hub: `eclipse-temurin:21-jdk-alpine` (builder), `eclipse-temurin:21-jre-alpine` (runtime)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
