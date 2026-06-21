package com.example.youtubeplaylistapi.controller;

import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.exception.InvalidPageTokenException;
import com.example.youtubeplaylistapi.service.PlaylistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaylistController implements PlaylistApi {

    private static final Logger log = LoggerFactory.getLogger(PlaylistController.class);

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Override
    public ResponseEntity<PlaylistResponse> getPlaylistItems(String playlistId, String pageToken) {
        log.info("endpoint={} playlistId={} pageToken={}", "GET /api/youtube/playlists/{playlistId}",
                playlistId, pageToken != null ? "present" : "absent");

        if (pageToken != null && pageToken.isBlank()) {
            pageToken = null;
        }
        if (pageToken != null && !pageToken.matches("^[A-Za-z0-9_\\-=]+$")) {
            throw new InvalidPageTokenException();
        }

        PlaylistResponse response = playlistService.getPlaylist(playlistId, pageToken);
        log.info("endpoint={} totalResults={}", "GET /api/youtube/playlists/{playlistId}",
                response.getTotalResults());
        return ResponseEntity.ok(response);
    }
}
