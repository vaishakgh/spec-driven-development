# 9. Rollout and Cutover

**Shadow mode requirement.** The Spring Boot service must be deployed and running in parallel with the Mule service, with production request traffic replayed against both services and responses compared field-by-field, before any traffic is switched. The mechanism is a request-replay harness: a script or tool captures live requests from the Mule deployment and issues identical requests to the Spring Boot deployment, comparing response bodies, status codes, and headers. Shadow mode is a hard gate — not advisory. Parity is confirmed when all tested endpoint + error-path combinations produce identical responses (or a documented, intentional deviation such as the FR-7 404 correction).

**Breaking change notification.** The correction to `GET /youtube/song/{videoId}` (HTTP 404 instead of HTTP 200-with-nulls for non-existent video IDs) is a breaking change in observable API behaviour. The team lead responsible for this migration sends a written notification (email or team channel) to all internal API consumers with a minimum of 3 working days' notice before cutover. Cutover does not proceed until the consuming team confirms their calling code has been updated to handle HTTP 404.

**Cutover sequence:**
1. Shadow mode parity confirmed across all endpoints and error paths.
2. Internal team notified of the HTTP 404 breaking change; team confirms calling code is updated.
3. Traffic redirected from Mule/CloudHub to Spring Boot.
4. Monitor for 48 hours: error rate, latency, and response correctness.
5. If stable, cancel CloudHub subscription for `youtube-playlist-api`.
6. Archive Mule project source to Git (do not delete).

**Rollback plan.** The Mule/CloudHub deployment remains active until step 5. If a regression is found post-cutover, traffic is immediately reverted to Mule. The Spring Boot deployment is left running for diagnosis.

---
