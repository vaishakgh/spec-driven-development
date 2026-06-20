# 1. Vision

`youtube-playlist-api` is a lightweight integration proxy that exposes two REST endpoints to an internal team: one to retrieve items from a YouTube playlist, and one to retrieve metadata for a single YouTube video. It sits between internal consumers and the YouTube Data API v3, handling authentication, response transformation, and error normalisation so callers never need a YouTube API key or knowledge of YouTube's raw response shapes.

The service currently runs as a Mule 4.6.0 application on CloudHub (MICRO worker). The migration replaces the Mule runtime and CloudHub platform with a Spring Boot application on Kubernetes without changing the observable contract of the API for existing callers — with two deliberate exceptions: a corrected error response for unknown video IDs, and newly exposed pagination support.

The migration is driven by four compounding motivations: elimination of MuleSoft licensing and CloudHub operating costs, consolidation onto a single platform and delivery pipeline, alignment of the integration layer with the team's Java/Spring expertise, and removal of a dependency on a vendor-managed runtime for a service of this scale. Success is the CloudHub subscription for this application cancelled with the internal team's workflow uninterrupted.

---
