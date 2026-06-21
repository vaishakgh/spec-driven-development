package com.example.youtubeplaylistapi.controller;

import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.exception.GlobalExceptionHandler;
import com.example.youtubeplaylistapi.service.PlaylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlaylistControllerTest {

    @Mock
    private PlaylistService playlistService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        PlaylistController controller = new PlaylistController(playlistService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void should_return_200_when_valid_playlist_id_no_page_token() throws Exception {
        PlaylistResponse response = buildResponse(50, "EAAelgEKADiD");
        when(playlistService.getPlaylist("PLxxx", null)).thenReturn(response);

        mockMvc.perform(get("/api/youtube/playlists/PLxxx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalResults").value(50))
            .andExpect(jsonPath("$.nextPageToken").value("EAAelgEKADiD"));
    }

    @Test
    void should_return_200_when_valid_page_token_provided() throws Exception {
        PlaylistResponse response = buildResponse(50, null);
        when(playlistService.getPlaylist("PLxxx", "EAAelgEKADiD")).thenReturn(response);

        mockMvc.perform(get("/api/youtube/playlists/PLxxx").param("pageToken", "EAAelgEKADiD"))
            .andExpect(status().isOk());

        verify(playlistService).getPlaylist("PLxxx", "EAAelgEKADiD");
    }

    @Test
    void should_return_400_when_page_token_is_malformed() throws Exception {
        mockMvc.perform(get("/api/youtube/playlists/PLxxx").param("pageToken", "bad token!!"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(playlistService);
    }

    @Test
    void should_return_200_with_empty_playlist_when_no_items() throws Exception {
        PlaylistResponse response = buildResponse(0, null);
        response.setPlaylist(List.of());
        when(playlistService.getPlaylist("PLxxx", null)).thenReturn(response);

        mockMvc.perform(get("/api/youtube/playlists/PLxxx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalResults").value(0))
            .andExpect(jsonPath("$.playlist").isEmpty());
    }

    private PlaylistResponse buildResponse(int totalResults, String nextPageToken) {
        PlaylistResponse r = new PlaylistResponse();
        r.setTotalResults(totalResults);
        r.setResultsPerPage(25);
        r.setNextPageToken(nextPageToken);
        r.setPlaylist(List.of());
        return r;
    }
}
