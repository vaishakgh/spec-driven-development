# 3. Glossary

- **Playlist** — An ordered collection of YouTube videos identified by a YouTube playlist ID (e.g. `PLbpi6ZahtOH6Ar_3GPy3workP3aUa5QGD`).
- **PlaylistItem** — A single video entry within a Playlist, as returned by the migrated API (not to be confused with the raw YouTube API `playlistItem` resource shape).
- **PlaylistResponse** — The complete response body returned by the Playlist endpoint: pagination metadata plus an array of PlaylistItems.
- **SongDetail** — The response body returned by the Video endpoint: metadata for a single YouTube video. The name is inherited from the existing RAML spec and retained for backward compatibility.
- **ErrorResponse** — The standard error body returned on all error conditions: `{ error, message, code }`.
- **VideoId** — The 11-character YouTube video identifier (e.g. `dQw4w9WgXcQ`).
- **PlaylistId** — The YouTube playlist identifier string (e.g. `PLxxxxxx`).
- **pageToken** — A pagination cursor value returned as `nextPageToken` in a PlaylistResponse. Passed as an input query parameter to retrieve the next page.
- **YouTube API key** — The credential used by the service to authenticate outbound requests to the YouTube Data API v3. Never exposed to API consumers.
- **Shadow mode** — A validation period during which the Spring Boot service processes requests in parallel with the Mule service, with responses compared for parity before traffic is switched.
- **Cutover** — The moment traffic is fully redirected from the Mule/CloudHub deployment to the Spring Boot deployment.

---
