# Test Migration Plan — MUnit to JUnit 5 + WireMock

**Date:** 2026-06-19
**Source:** `src/test/munit/youtube-playlist-test.xml` (7 MUnit 3.7.0 tests)
**Target:** JUnit 5 + WireMock + AssertJ + Spring Boot Test

---

## Test Framework Mapping

| MUnit Concept | JUnit 5 + Spring Equivalent |
|---|---|
| `<munit:config>` | `@ExtendWith(SpringExtension.class)` + `@SpringBootTest` |
| `<global-property name="env" value="test">` | `@ActiveProfiles("test")` on test class |
| `<munit:test name="..." description="...">` | `@Test` method + `@DisplayName("...")` |
| `<munit:behavior>` (test setup) | `@BeforeEach` + WireMock stub setup |
| `<munit-tools:mock-when processor="http:request">` | `WireMockServer.stubFor(get(...).willReturn(...))` |
| `<munit-tools:with-attribute attributeName="path" whereValue="/playlistItems">` | `urlPathEqualTo("/youtube/v3/playlistItems")` in WireMock |
| `<munit-tools:then-return>` with `<munit-tools:payload>` | `WireMock.aResponse().withBody(json)` |
| `<munit-tools:error typeId="HTTP:UNAUTHORIZED">` | `WireMock.aResponse().withStatus(401)` |
| `<munit-tools:error typeId="HTTP:CONNECTIVITY">` | `WireMockServer.stop()` + expect `WebClientRequestException` |
| `<munit-tools:error typeId="MULE:UNKNOWN">` | `WireMock.aResponse().withStatus(500)` |
| `<munit:execution>` + `<flow-ref>` | `mockMvc.perform(get("/api/youtube/playlists/..."))` |
| `<munit:set-event>` with `<munit:attributes>` | MockMvc request builder with path variables |
| `<munit-tools:assert-that expression="#[...]" is="#[MunitTools::equalTo(...)]">` | `assertThat(response.getBody()).isEqualTo(...)` via AssertJ |
| `expectedErrorType="HTTP:UNAUTHORIZED"` | `andExpect(status().isUnauthorized())` |

---

## WireMock Setup Pattern

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class YouTubePlaylistControllerTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Autowired
    private MockMvc mockMvc;

    // Override youtube.api.base-url to point to WireMock in test profile
    // application-test.yml: youtube.api.base-url: localhost:${wiremock.server.port}
}
```

**Alternative:** Use `@DynamicPropertySource` to inject WireMock port into Spring context.

---

## Test-by-Test Migration Plan

### Test 1: `test-get-youtube-playlists-success` → 200 Success

**MUnit approach:** Mock `http:request` → `/playlistItems`, return 2-item playlist payload; assert `totalResults=2`, `playlist.size=2`, `playlist[0].title="Song One"`.

**JUnit 5 equivalent:**
```java
@Test
@DisplayName("GET /youtube/playlists/{playlistId} — 200 with 2-item playlist")
void getPlaylist_success_returns200WithPlaylistItems() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/youtube/v3/playlistItems"))
        .withQueryParam("playlistId", equalTo("PLtest12345"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBodyFile("youtube-playlist-2-items.json")));  // test fixture

    mockMvc.perform(get("/api/youtube/playlists/PLtest12345"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalResults").value(2))
        .andExpect(jsonPath("$.playlist.length()").value(2))
        .andExpect(jsonPath("$.playlist[0].title").value("Song One"))
        .andExpect(jsonPath("$.playlist[0].videoId").value("abc123"))
        .andExpect(jsonPath("$.playlist[0].videoUrl").value("https://www.youtube.com/watch?v=abc123"))
        .andExpect(jsonPath("$.nextPageToken").value("token123"));
}
```

---

### Test 2: `test-get-youtube-playlists-empty` → Empty playlist

**JUnit 5 equivalent:**
```java
@Test
@DisplayName("GET /youtube/playlists/{playlistId} — 200 with empty playlist")
void getPlaylist_emptyPlaylist_returns200WithZeroResults() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/youtube/v3/playlistItems"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBodyFile("youtube-playlist-empty.json")));

    mockMvc.perform(get("/api/youtube/playlists/PLempty"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalResults").value(0))
        .andExpect(jsonPath("$.playlist.length()").value(0));
}
```

---

### Test 3: `test-get-youtube-playlists-unauthorized` → 401

**MUnit approach:** Mock throws `HTTP:UNAUTHORIZED`, expects `expectedErrorType="HTTP:UNAUTHORIZED"`.

**JUnit 5 equivalent:**
```java
@Test
@DisplayName("GET /youtube/playlists/{playlistId} — 401 when YouTube API key is invalid")
void getPlaylist_unauthorized_returns401() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/youtube/v3/playlistItems"))
        .willReturn(aResponse().withStatus(401)));

    mockMvc.perform(get("/api/youtube/playlists/PLbadkey"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(jsonPath("$.code").value(401));
}
```

---

### Test 4: `test-get-youtube-playlists-connectivity-error` → 503

**MUnit approach:** Mock throws `HTTP:CONNECTIVITY`, expects `expectedErrorType="HTTP:CONNECTIVITY"`.

**JUnit 5 equivalent:**
```java
@Test
@DisplayName("GET /youtube/playlists/{playlistId} — 503 when YouTube API is unreachable")
void getPlaylist_connectivityError_returns503() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/youtube/v3/playlistItems"))
        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    mockMvc.perform(get("/api/youtube/playlists/PLoffline"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("Connection Error"))
        .andExpect(jsonPath("$.code").value(503));
}
```

---

### Test 5: `test-get-youtube-song-by-id-success` → 200 Success

**JUnit 5 equivalent:**
```java
@Test
@DisplayName("GET /youtube/song/{videoId} — 200 with full song details")
void getSong_success_returns200WithSongDetails() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/youtube/v3/videos"))
        .withQueryParam("id", equalTo("dQw4w9WgXcQ"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBodyFile("youtube-video-rickroll.json")));

    mockMvc.perform(get("/api/youtube/song/dQw4w9WgXcQ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoId").value("dQw4w9WgXcQ"))
        .andExpect(jsonPath("$.title").value("Never Gonna Give You Up"))
        .andExpect(jsonPath("$.channelName").value("RickAstleyVEVO"))
        .andExpect(jsonPath("$.duration").value("PT3M33S"))
        .andExpect(jsonPath("$.viewCount").value("1400000000"))
        .andExpect(jsonPath("$.videoUrl").value("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        .andExpect(jsonPath("$.thumbnail").isNotEmpty());
}
```

---

### Test 6: `test-get-youtube-song-not-found` → Empty response

**MUnit note:** Mule returns 200 with null/empty fields when `items[]` is empty. The Spring Boot implementation should replicate this behaviour for parity.

```java
@Test
@DisplayName("GET /youtube/song/{videoId} — 200 with null fields when video not found")
void getSong_notFound_returns200WithNullVideoId() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/youtube/v3/videos"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"kind\":\"youtube#videoListResponse\",\"items\":[]}")));

    mockMvc.perform(get("/api/youtube/song/INVALID_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoId").isEmpty())
        .andExpect(jsonPath("$.videoUrl").value("https://www.youtube.com/watch?v="));
}
```

**PRD note:** This is a functional concern worth raising — returning 200 with null fields for a not-found video is unusual. Consider returning 404 in the Spring Boot version.

---

### Test 7: `test-get-youtube-song-generic-error` → 500

```java
@Test
@DisplayName("GET /youtube/song/{videoId} — 500 on unexpected YouTube API error")
void getSong_genericError_returns500() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/youtube/v3/videos"))
        .willReturn(aResponse().withStatus(500)));

    mockMvc.perform(get("/api/youtube/song/errorId"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("Internal Server Error"))
        .andExpect(jsonPath("$.code").value(500));
}
```

---

## Test File Structure (Spring Boot)

```
src/test/
├── java/
│   └── com/example/youtubeplaylistapi/
│       ├── controller/
│       │   ├── PlaylistControllerTest.java      # Tests 1-4
│       │   └── SongControllerTest.java          # Tests 5-7
│       └── mapper/
│           ├── PlaylistMapperTest.java          # Unit tests for DW-1 mapping
│           └── SongMapperTest.java              # Unit tests for DW-2 mapping
└── resources/
    ├── application-test.yml                     # Test profile config
    └── __files/                                 # WireMock response fixtures
        ├── youtube-playlist-2-items.json
        ├── youtube-playlist-empty.json
        └── youtube-video-rickroll.json
```

---

## Additional Tests Recommended (Beyond MUnit Coverage)

| Test | Rationale |
|---|---|
| `PlaylistMapperTest` — assert all fields mapped correctly | Pure unit test of DW-1 mapping; no HTTP needed |
| `SongMapperTest` — assert field rename `channelTitle→channelName` | Pure unit test of DW-2 mapping |
| `getPlaylist_missingApiKey_returns401` | Validate error handling when API key is empty/null |
| `getSong_youtubeApiReturns429_returns503OrForwards429` | YouTube API rate limiting scenario |
| `getPlaylist_largePlaylist_paginationTokenPresent` | Verify `nextPageToken` is correctly passed through |
