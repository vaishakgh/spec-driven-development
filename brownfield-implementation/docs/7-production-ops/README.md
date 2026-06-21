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
