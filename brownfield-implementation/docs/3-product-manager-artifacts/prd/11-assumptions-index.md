# 11. Assumptions Index

- **[A1 — RESOLVED, §4.1 FR-3]** Invalid `pageToken` returns HTTP 400. The service validates the token format locally; upstream rejection also propagates as HTTP 400. Decision recorded in decision log entry #11.
- **[ASSUMPTION A2, §4.2 FR-6]** `viewCount` and `likeCount` are returned as strings (not integers) to match the upstream YouTube API representation and preserve the existing contract.
- **[ASSUMPTION A3, §4.6 FR-22]** The existing Mule logging verbosity (INFO level for request + result, ERROR for failures) is the right baseline. No additional audit logging (e.g. full request/response bodies) is required.
- **[ASSUMPTION A4, §6.2]** The internal team has no current demand for caller-controllable `maxResults`. If this surfaces post-migration, it is a v2 enhancement.
- **[ASSUMPTION A5, §9]** The internal team's calling code can be updated before cutover. If the team requires an extended parallel-run period, the shadow mode window extends accordingly — the cutover date is not fixed.
