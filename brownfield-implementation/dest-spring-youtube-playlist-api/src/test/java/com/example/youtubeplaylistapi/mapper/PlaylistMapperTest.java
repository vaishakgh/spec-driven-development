package com.example.youtubeplaylistapi.mapper;

import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.dto.youtube.YTPlaylistItemsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaylistMapperTest {

    private final PlaylistMapper mapper = new PlaylistMapper();

    @Test
    void should_map_all_fields_when_full_youtube_response() {
        YTPlaylistItemsResponse yt = buildFullResponse();

        PlaylistResponse result = mapper.toPlaylistResponse(yt);

        assertThat(result.getTotalResults()).isEqualTo(50);
        assertThat(result.getResultsPerPage()).isEqualTo(25);
        assertThat(result.getNextPageToken()).isEqualTo("EAAelgEKADiD");
        assertThat(result.getPlaylist()).hasSize(1);

        var item = result.getPlaylist().get(0);
        assertThat(item.getPosition()).isEqualTo(0);
        assertThat(item.getVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(item.getTitle()).isEqualTo("Never Gonna Give You Up");
        assertThat(item.getDescription()).isEqualTo("The official music video.");
        assertThat(item.getPublishedAt()).isEqualTo("2009-10-25T06:57:33Z");
        assertThat(item.getThumbnail()).isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg");
        assertThat(item.getVideoUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void should_return_null_thumbnail_when_medium_thumbnail_missing() {
        YTPlaylistItemsResponse yt = buildFullResponse();
        yt.getItems().get(0).getSnippet().getThumbnails().setMedium(null);

        PlaylistResponse result = mapper.toPlaylistResponse(yt);

        assertThat(result.getPlaylist().get(0).getThumbnail()).isNull();
    }

    @Test
    void should_return_empty_playlist_when_items_array_is_empty() {
        YTPlaylistItemsResponse yt = new YTPlaylistItemsResponse();
        var pageInfo = new YTPlaylistItemsResponse.PageInfo();
        pageInfo.setTotalResults(0);
        pageInfo.setResultsPerPage(25);
        yt.setPageInfo(pageInfo);
        yt.setItems(List.of());

        PlaylistResponse result = mapper.toPlaylistResponse(yt);

        assertThat(result.getTotalResults()).isEqualTo(0);
        assertThat(result.getPlaylist()).isEmpty();
        assertThat(result.getNextPageToken()).isNull();
    }

    // --- helper ---

    private YTPlaylistItemsResponse buildFullResponse() {
        var thumbnail = new YTPlaylistItemsResponse.Item.Snippet.Thumbnails.Thumbnail();
        thumbnail.setUrl("https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg");

        var thumbnails = new YTPlaylistItemsResponse.Item.Snippet.Thumbnails();
        thumbnails.setMedium(thumbnail);

        var resourceId = new YTPlaylistItemsResponse.Item.Snippet.ResourceId();
        resourceId.setVideoId("dQw4w9WgXcQ");

        var snippet = new YTPlaylistItemsResponse.Item.Snippet();
        snippet.setTitle("Never Gonna Give You Up");
        snippet.setDescription("The official music video.");
        snippet.setPublishedAt("2009-10-25T06:57:33Z");
        snippet.setPosition(0);
        snippet.setThumbnails(thumbnails);
        snippet.setResourceId(resourceId);

        var item = new YTPlaylistItemsResponse.Item();
        item.setSnippet(snippet);

        var pageInfo = new YTPlaylistItemsResponse.PageInfo();
        pageInfo.setTotalResults(50);
        pageInfo.setResultsPerPage(25);

        var yt = new YTPlaylistItemsResponse();
        yt.setNextPageToken("EAAelgEKADiD");
        yt.setPageInfo(pageInfo);
        yt.setItems(List.of(item));
        return yt;
    }
}
