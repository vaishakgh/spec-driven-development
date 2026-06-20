# 10. Open Questions

1. **Consumer calling-code update timeline.** How much lead time does the internal team need to update calling code to handle HTTP 404 from `GET /youtube/song/{videoId}` before cutover can proceed?
2. **YouTube API quota.** Is the current YouTube Data API v3 quota sufficient post-migration? The key is the same; usage patterns should not change, but this should be confirmed with the team managing the API key.
3. **Deployment environment.** Which Kubernetes cluster / namespace is the target for the initial deployment (local → dev → prod path)?

---
