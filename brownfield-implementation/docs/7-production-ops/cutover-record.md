# Migration Cutover Record

**Project:** YouTube Playlist API — MuleSoft CloudHub to Spring Boot on Kubernetes
**Migration Owner:** [Team Lead Name]
**Story Reference:** 7.3

---

## Pre-Cutover Gate Checklist

Complete all gates before sending consumer notification.

| Gate | Command | Expected | Status |
|------|---------|----------|--------|
| Tests green | `./mvnw test` | BUILD SUCCESS, Failures: 0 | [ ] PENDING |
| Shadow mode PASSED | `python3 docs/7-production-ops/compare.py --mule <url> --spring <url>` | "SHADOW MODE GATE: PASSED" | [ ] PENDING |
| Liveness probe UP | `curl http://<k8s>/actuator/health/liveness` | `{"status":"UP"}` | [ ] PENDING |
| Readiness probe UP | `curl http://<k8s>/actuator/health/readiness` | `{"status":"UP"}` | [ ] PENDING |

---

## Consumer Team Notification

**Notification date:** [DATE]
**Acknowledgement deadline:** [DATE + 3 working days]
**Acknowledgement received:** [ ] YES  [ ] NO

### Notification Template

```
Subject: [Action Required by <DATE+3 working days>] YouTube Playlist API Migration — Breaking Change Notice

Team,

We are migrating the YouTube Playlist API from MuleSoft CloudHub to Spring Boot on Kubernetes.
The migration is scheduled to complete on <CUTOVER_DATE>.

**Action Required:** Please review the breaking change below and confirm no-objection by <DATE+3 working days>.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BREAKING CHANGE — FR-7
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Endpoint: GET /api/youtube/song/{videoId}

BEFORE (Mule):
  When the videoId does not exist in YouTube:
  → HTTP 200 with null fields: { "videoId": null, "videoUrl": "https://www.youtube.com/watch?v=", ... }

AFTER (Spring Boot):
  When the videoId does not exist in YouTube:
  → HTTP 404 with: { "error": "Not Found", "message": "Video not found for the given videoId.", "code": 404 }

Required consumer action: Update any consumer code that checks for null videoId in a 200 response
to instead handle HTTP 404. All other endpoints and fields are unchanged.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

No other breaking changes. All other fields and response shapes are identical.

The parallel Mule service will remain running for at least 48 hours after cutover before decommission,
providing a rollback path if any unexpected issues arise.

Please reply with acknowledgement or concerns.

— <Team Lead Name>
```

---

## Traffic Switch

**Cutover date:** [DATE]
**DNS / Load Balancer change:** [ ] APPLIED
**Validation after switch:** [ ] CONFIRMED

```bash
# After DNS update — confirm response from Spring Boot
curl -v https://<api-domain>/api/youtube/playlists/<realPlaylistId>

# Health still green
curl -s https://<api-domain>/actuator/health/readiness
```

**NOTE:** Do NOT stop Mule worker immediately after traffic switch.

---

## Stability Observation Period

**Observation start:** [DATE]
**Observation end:** [DATE + 48 hours minimum]

| Check | Result |
|-------|--------|
| Spring Boot logs — no unexpected `log.error` entries | [ ] CLEAR |
| K8s pod health — no pod restarts | [ ] CLEAR |
| Readiness probe — stays green throughout | [ ] CLEAR |

```bash
# Monitor pods
kubectl get pods -l app=youtube-playlist-api

# Follow logs
kubectl logs -l app=youtube-playlist-api -f
```

---

## CloudHub Decommission

Execute only after observation period clears with no incidents.

| Step | Status |
|------|--------|
| Stop CloudHub MICRO worker in Anypoint Runtime Manager | [ ] DONE |
| Cancel Anypoint Platform subscription (or downgrade to free tier) | [ ] DONE |
| Update internal team documentation — remove Mule endpoint references | [ ] DONE |

---

## Final Validation

| Check | Result |
|-------|--------|
| `GET /actuator/health/readiness` returns `{"status":"UP"}` on production | [ ] CONFIRMED |
| Mule CloudHub worker status: STOPPED | [ ] CONFIRMED |
| Migration recorded as COMPLETE | [ ] CONFIRMED |

---

## Post-Migration Final State

```
✓ DNS → Spring Boot on Kubernetes (port 8081)
✓ GET /actuator/health/readiness → { "status": "UP" }
✓ Mule CloudHub worker: STOPPED
✓ Anypoint subscription: CANCELLED
✓ All 8 integration tests: PASSING
✓ Shadow mode gate: PASSED (on record)
✓ Consumer team notified of FR-7 breaking change
✓ CloudHub cost: ELIMINATED
```

---

## Rollback Plan

If critical issues occur within the observation period:

1. Revert DNS/load balancer to point back to Mule CloudHub — immediate rollback (Mule worker still running)
2. Investigate Spring Boot logs: `kubectl logs -l app=youtube-playlist-api -f`
3. Fix issue → re-run shadow mode → re-do traffic switch
