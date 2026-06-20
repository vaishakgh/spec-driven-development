# 8. Success Metrics

**Primary**

- **SM-1:** CloudHub subscription for `youtube-playlist-api` cancelled — confirms the migration cost objective is achieved. Validates FR-17 to FR-20.
- **SM-2:** All 7 migrated test scenarios passing in CI — confirms behavioral parity is code-verified. Validates FR-1 through FR-15.
- **SM-3:** Shadow mode response comparison shows ≤ 0% payload divergence across all tested endpoint + error-path combinations before cutover. Validates FR-1 through FR-15.

**Secondary**

- **SM-4:** Spring Boot P95 response latency ≤ 120% of Mule/CloudHub P95 at equivalent load — measured during shadow mode. Validates NFR-1.
- **SM-5:** Zero consumer-reported incidents in the 2 weeks following cutover.

**Counter-metrics (do not optimise)**

- **SM-C1:** Error rate post-cutover. Must not increase relative to the Mule baseline. Counterbalances SM-1 — decommissioning faster than the parity gate allows is not success.
- **SM-C2:** Memory usage. Reducing memory below NFR-4's 512 MB limit is not a target; stability within the limit is. Counterbalances SM-4 — micro-optimising for resource use at the cost of readability or maintainability is not success.

---
