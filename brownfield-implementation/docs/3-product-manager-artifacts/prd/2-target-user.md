# 2. Target User

## 2.1 Jobs To Be Done

- Retrieve an ordered list of videos from a known YouTube playlist, including titles, descriptions, thumbnails, and direct watch URLs — without managing a YouTube API key.
- Retrieve detailed metadata for a specific YouTube video (duration, view count, like count, channel name) by video ID.
- Page through large playlists that exceed a single response page.
- Receive consistent, structured error responses so that error handling in calling code is simple and predictable.

## 2.2 Non-Users (v1)

- External/third-party consumers — this API is internal only.
- End users interacting through a UI — this is an API-to-API integration layer.

## 2.3 Key User Journey

**UJ-1. Developer on the internal team fetches playlist data to populate a view.**
Developer calls `GET /api/youtube/playlists/{playlistId}` with a known playlist ID. The service returns a JSON object with total results, results per page, an optional next-page token, and an array of playlist items — each containing the video ID, title, description, published date, thumbnail URL, and a pre-constructed watch URL. If the playlist spans multiple pages, the developer uses the returned `nextPageToken` value as the `pageToken` query parameter on the next call to retrieve the following page.

---
