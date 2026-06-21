---
baseline_commit: 9b9d277885d0e71d46de200e8839fa49a16c7ae8
---

# Story 7.2: Implement Shadow Mode Request-Replay Harness

Status: done

## Story

As an **internal developer**,
I want a request-replay harness that sends identical requests to both the Mule and Spring Boot services and compares responses field-by-field,
so that we have objective evidence of response parity before any traffic is switched to the Spring Boot service.

## Acceptance Criteria

1. **Given** both the Mule service (CloudHub) and Spring Boot service (Kubernetes) are running simultaneously **When** the harness replays a representative set of real requests against both **Then** responses from both services are compared field-by-field for both endpoints **And** any field-level discrepancies are reported with the request payload, expected value, and actual value
2. **Given** the harness completes a full run with zero discrepancies **When** the results are reviewed **Then** the shadow mode gate is recorded as PASSED — cutover may proceed
3. **Given** the harness detects any field-level mismatch **When** the results are reviewed **Then** the shadow mode gate is recorded as FAILED — cutover is blocked until the mismatch is resolved **And** latency of the Spring Boot service is captured during shadow mode runs to verify NFR-1 (≤20% regression vs Mule baseline)

## Tasks / Subtasks

- [x] Create `docs/7-production-ops/` directory under docs/ (AC: 1, 2, 3)
- [x] Create `docs/7-production-ops/compare.py` — the harness script (AC: 1, 2, 3)
  - [x] Read Mule URL and Spring Boot URL from env vars or CLI args
  - [x] Define representative request set: at least 1 real `playlistId`, 1 real `videoId`
  - [x] Send GET requests to both endpoints on both services
  - [x] Deep compare responses field-by-field (JSON diff)
  - [x] Capture and compare latency for NFR-1
  - [x] Print PASS/FAIL gate result to stdout
- [x] Create `docs/7-production-ops/README.md` with usage instructions (AC: 2, 3)
- [x] Run harness manually against both live services to validate it works — NOTE: Both Mule CloudHub and Spring Boot K8s services are not yet running in the local environment. Python syntax validated (`python3 -m py_compile`). Full validation requires live services per story context.

## Dev Notes

### Context: Shadow Mode in the Migration Strategy

Shadow mode runs after deployment (Story 7.1) with BOTH systems live:
- **Mule service:** CloudHub, still receiving production traffic
- **Spring Boot service:** Kubernetes, receiving shadow (replayed) traffic only

The harness sends the SAME requests to both. Mismatches indicate migration bugs that must be fixed before cutover. Zero mismatches → cutover can proceed.

### Harness Location

The harness lives under `docs/7-production-ops/`:
```
brownfield-implementation/
├── dest-spring-youtube-playlist-api/   # Spring Boot project
├── source-mule-youtube-playlist-api/   # Reference Mule project
└── docs/
    └── 7-production-ops/
        ├── compare.py                  # ← harness script
        ├── README.md                   # ← usage instructions
        └── cutover-record.md           # ← cutover runbook
```

### Complete Harness Implementation (`docs/7-production-ops/compare.py`)

```python
#!/usr/bin/env python3
"""
Shadow Mode Request-Replay Harness
Sends identical requests to Mule (source) and Spring Boot (target),
compares responses field-by-field, and reports PASS/FAIL.

Usage:
    MULE_BASE_URL=https://mule.example.cloudhub.io/api
    SPRING_BASE_URL=http://k8s-ingress.example.com/api
    YOUTUBE_API_KEY=<key>  # or use --no-auth for unauthenticated test endpoints

    python3 compare.py --mule $MULE_BASE_URL --spring $SPRING_BASE_URL
"""

import argparse
import json
import sys
import time
from typing import Any

try:
    import requests
except ImportError:
    print("ERROR: 'requests' library not installed. Run: pip install requests")
    sys.exit(1)


# ── Request set ──────────────────────────────────────────────────────────────
# Edit these to use real playlist/video IDs from your production environment.

REQUEST_SCENARIOS = [
    {
        "name": "playlist-happy-path",
        "path": "/youtube/playlists/PLtest12345",
        "params": {},
    },
    {
        "name": "playlist-with-page-token",
        "path": "/youtube/playlists/PLtest12345",
        "params": {"pageToken": "EAAelgEKADiD"},
    },
    {
        "name": "video-happy-path",
        "path": "/youtube/song/dQw4w9WgXcQ",
        "params": {},
    },
    {
        "name": "video-not-found",
        "path": "/youtube/song/INVALID_VIDEO_ID",
        "params": {},
        "expect_status_mismatch": True,  # Mule 200, Spring 404 — FR-7 known difference
    },
]

# Fields to EXCLUDE from comparison (known intentional differences)
IGNORED_FIELDS: set[str] = set()


# ── Core comparison logic ─────────────────────────────────────────────────────

def deep_diff(mule: Any, spring: Any, path: str = "$") -> list[dict]:
    """Recursively compare two JSON objects. Returns list of field-level diffs."""
    diffs = []

    if type(mule) != type(spring):
        diffs.append({"path": path, "mule": mule, "spring": spring})
        return diffs

    if isinstance(mule, dict):
        all_keys = set(mule.keys()) | set(spring.keys())
        for key in sorted(all_keys):
            if key in IGNORED_FIELDS:
                continue
            child_path = f"{path}.{key}"
            if key not in mule:
                diffs.append({"path": child_path, "mule": "(missing)", "spring": spring[key]})
            elif key not in spring:
                diffs.append({"path": child_path, "mule": mule[key], "spring": "(missing)"})
            else:
                diffs.extend(deep_diff(mule[key], spring[key], child_path))
    elif isinstance(mule, list):
        if len(mule) != len(spring):
            diffs.append({"path": f"{path}[length]", "mule": len(mule), "spring": len(spring)})
        for i, (m, s) in enumerate(zip(mule, spring)):
            diffs.extend(deep_diff(m, s, f"{path}[{i}]"))
    else:
        if mule != spring:
            diffs.append({"path": path, "mule": mule, "spring": spring})

    return diffs


def call_endpoint(base_url: str, path: str, params: dict) -> tuple[int, Any, float]:
    """Call an endpoint. Returns (status_code, response_body_as_dict, latency_ms)."""
    url = base_url.rstrip("/") + path
    t0 = time.perf_counter()
    resp = requests.get(url, params=params, timeout=10)
    latency_ms = (time.perf_counter() - t0) * 1000.0
    try:
        body = resp.json()
    except Exception:
        body = {"_raw": resp.text}
    return resp.status_code, body, latency_ms


# ── Runner ────────────────────────────────────────────────────────────────────

def run(mule_base: str, spring_base: str) -> bool:
    """Run all scenarios. Returns True if shadow gate PASSES."""
    all_pass = True
    latency_results = []

    print(f"\n{'=' * 70}")
    print(f"Shadow Mode Harness")
    print(f"  Mule   : {mule_base}")
    print(f"  Spring : {spring_base}")
    print(f"{'=' * 70}\n")

    for scenario in REQUEST_SCENARIOS:
        name = scenario["name"]
        path = scenario["path"]
        params = scenario["params"]
        expect_status_mismatch = scenario.get("expect_status_mismatch", False)

        print(f"▶  {name}")
        print(f"   GET {path} params={params or '(none)'}")

        mule_status, mule_body, mule_latency = call_endpoint(mule_base, path, params)
        spring_status, spring_body, spring_latency = call_endpoint(spring_base, path, params)

        latency_results.append({
            "scenario": name,
            "mule_ms": round(mule_latency, 1),
            "spring_ms": round(spring_latency, 1),
            "ratio": round(spring_latency / mule_latency, 2) if mule_latency > 0 else None,
        })

        # Status check
        if mule_status != spring_status:
            if expect_status_mismatch:
                print(f"   ℹ KNOWN STATUS DIFF: Mule={mule_status} Spring={spring_status} (FR-7 breaking change)")
            else:
                print(f"   ✗ STATUS MISMATCH: Mule={mule_status} Spring={spring_status}")
                all_pass = False
            print()
            continue

        # Field-level comparison (only when status matches)
        diffs = deep_diff(mule_body, spring_body)
        if diffs:
            print(f"   ✗ {len(diffs)} FIELD DIFF(S):")
            for d in diffs:
                print(f"     {d['path']}: Mule={d['mule']!r}  Spring={d['spring']!r}")
            all_pass = False
        else:
            print(f"   ✓ PASS  (Mule: {mule_status}/{mule_latency:.0f}ms  Spring: {spring_status}/{spring_latency:.0f}ms)")
        print()

    # Latency summary (NFR-1: ≤20% regression vs Mule baseline)
    print(f"{'─' * 70}")
    print("Latency Summary (NFR-1: Spring must not exceed Mule by >20%)")
    nfr1_pass = True
    for r in latency_results:
        flag = ""
        if r["ratio"] and r["ratio"] > 1.20:
            flag = " ← NFR-1 VIOLATION"
            nfr1_pass = False
            all_pass = False
        print(f"  {r['scenario']:<40} Mule={r['mule_ms']:>6}ms  Spring={r['spring_ms']:>6}ms  ratio={r['ratio']}{flag}")

    print(f"\n{'=' * 70}")
    if all_pass:
        print("SHADOW MODE GATE: PASSED ✓  — Cutover may proceed.")
    else:
        print("SHADOW MODE GATE: FAILED ✗  — Resolve all mismatches before cutover.")
    print(f"{'=' * 70}\n")

    return all_pass


# ── Entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Shadow mode request-replay harness")
    parser.add_argument("--mule", required=True, help="Mule CloudHub base URL, e.g. https://app.cloudhub.io/api")
    parser.add_argument("--spring", required=True, help="Spring Boot K8s base URL, e.g. http://svc.cluster.local/api")
    args = parser.parse_args()

    passed = run(args.mule, args.spring)
    sys.exit(0 if passed else 1)
```

### docs/7-production-ops/README.md

```markdown
# Shadow Mode Request-Replay Harness

## Purpose
Confirms response parity between the Mule (source) and Spring Boot (target) services before traffic cutover.

## Prerequisites
- Python 3.10+
- `pip install requests`
- Both services running and reachable

## Usage

```bash
python3 compare.py \
  --mule https://<mule-app>.cloudhub.io/api \
  --spring http://<k8s-ingress>/api
```

## Known Differences (Not Failures)

| Scenario | Mule | Spring Boot | Reason |
|---|---|---|---|
| GET /youtube/song/{nonExistentId} | HTTP 200, null fields | HTTP 404, ErrorResponse | FR-7 breaking change — intentional |

## Gate Criteria

- PASSED: Zero field-level mismatches + Spring latency ≤ 120% of Mule latency (NFR-1)
- FAILED: Any field mismatch OR latency regression >20%

## Customising Scenarios

Edit `REQUEST_SCENARIOS` in `compare.py` with real playlist/video IDs from production before running.
```

### Key Implementation Notes

**`expect_status_mismatch: True` for video not-found scenario:**
The Mule implementation returns HTTP 200 with null fields for unknown video IDs. The Spring Boot implementation returns HTTP 404. This is FR-7 — an intentional breaking change. The harness marks this scenario as a known difference, not a failure. The consumer team notification (Story 7.3) covers this.

**NFR-1 latency check (≤20% regression):**
`spring_latency / mule_latency > 1.20` triggers a failure. Java 21 virtual threads should make Spring Boot latency comparable to Mule's async model. If this fails, investigate GC tuning or JIT warmup, not architecture.

**Dependency:** Python's `requests` library. No other dependencies. Script is intentionally self-contained.

**Request IDs:** Edit `REQUEST_SCENARIOS` before the real shadow run to use actual production playlist/video IDs from the Mule environment. The IDs shown (`PLtest12345`, `dQw4w9WgXcQ`) are placeholders.

### Project Structure Notes

- NEW: `docs/7-production-ops/compare.py`
- NEW: `docs/7-production-ops/README.md`
- No changes to Spring Boot source, pom.xml, or k8s manifests

### References

- [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-7-deployment-shadow-mode-cutover.md#Story 7.2]
- [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Shadow mode strategy (NFR-3)]
- NFR-1: ≤20% latency regression vs Mule baseline
- NFR-3: shadow mode request-replay before cutover gate
- FR-7: `GET /api/youtube/song/{videoId}` returns 404 (not 200) for unknown video — documented as known difference in harness

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Python syntax validation: `python3 -m py_compile docs/7-production-ops/compare.py` → OK on Python 3.9.6
- Live harness run cannot be performed in this environment (neither Mule CloudHub nor Spring Boot K8s service is running). The harness is ready to run once both services are deployed.

### Completion Notes List

- Created `docs/7-production-ops/compare.py` with 4 request scenarios: playlist-happy-path, playlist-with-page-token, video-happy-path, video-not-found
- `video-not-found` scenario marked `expect_status_mismatch: True` — FR-7 known difference (Mule 200, Spring 404)
- `deep_diff()` recursively compares JSON field-by-field, reports path + mule/spring values for each discrepancy
- `call_endpoint()` measures latency in ms; NFR-1 check: Spring latency must not exceed Mule by >20% (ratio > 1.20)
- PASS/FAIL gate result printed to stdout; exit code 0 (PASS) or 1 (FAIL) for CI integration
- Created `docs/7-production-ops/README.md` with usage instructions, known differences table, and gate criteria

### File List

- docs/7-production-ops/compare.py (new)
- docs/7-production-ops/README.md (new)

### Review Findings

- [x] [Review][Patch] `deep_diff()` uses `zip()` on lists — silently truncates comparison at the shorter list; extra elements in either response are never inspected, masking regressions — violates AC-1 field-by-field completeness [compare.py:83-85]
- [x] [Review][Patch] `type(mule) != type(spring)` identity check produces false positives for semantically equal JSON values of different Python numeric types (e.g. `int` vs `float`) [compare.py:65]
- [x] [Review][Patch] `requests.exceptions.Timeout` / `ConnectionError` not caught in `call_endpoint`; harness crashes with traceback, CI sees exit code 1 but not the structured FAILED message [compare.py:97]
- [x] [Review][Patch] `expect_status_mismatch: True` scenario still appends to `latency_results` before `continue`; a slow video-not-found response can trigger a spurious NFR-1 FAIL [compare.py:131-136]
- [x] [Review][Patch] Both services returning non-JSON (e.g. proxy HTML error page) causes `deep_diff` on `_raw` string values; if both return identical error text the scenario reports PASS with no warning [compare.py:100-103]
- [x] [Review][Defer] Placeholder IDs `PLtest12345` and `dQw4w9WgXcQ` in REQUEST_SCENARIOS; requires live services — acknowledged by dev agent, documented in README — deferred, pre-existing
- [x] [Review][Defer] `IGNORED_FIELDS` set has no path-scoping — any key name added suppresses that key globally; empty set currently makes this moot — deferred, pre-existing
