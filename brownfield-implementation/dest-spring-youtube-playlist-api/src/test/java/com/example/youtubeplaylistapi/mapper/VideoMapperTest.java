package com.example.youtubeplaylistapi.mapper;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.dto.youtube.YTVideoDetailsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoMapperTest {

    private final VideoMapper mapper = new VideoMapper();

    @Test
    void should_map_all_fields_when_full_youtube_response() {
        YTVideoDetailsResponse yt = buildFullResponse();

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result).isNotNull();
        assertThat(result.getVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(result.getTitle()).isEqualTo("Never Gonna Give You Up");
        assertThat(result.getDescription()).isEqualTo("The official music video.");
        assertThat(result.getChannelName()).isEqualTo("RickAstleyVEVO");  // channelTitle mapped to channelName
        assertThat(result.getPublishedAt()).isEqualTo("2009-10-25T06:57:33Z");
        assertThat(result.getDuration()).isEqualTo("PT3M33S");
        assertThat(result.getViewCount()).isEqualTo("1400000000");
        assertThat(result.getLikeCount()).isEqualTo("15000000");
        assertThat(result.getThumbnail()).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        assertThat(result.getVideoUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void should_return_null_thumbnail_when_high_thumbnail_missing() {
        YTVideoDetailsResponse yt = buildFullResponse();
        yt.getItems().get(0).getSnippet().getThumbnails().setHigh(null);

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result.getThumbnail()).isNull();
    }

    @Test
    void should_return_null_when_items_array_is_empty() {
        YTVideoDetailsResponse yt = new YTVideoDetailsResponse();
        yt.setItems(List.of());

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result).isNull();
    }

    @Test
    void should_return_null_when_snippet_is_absent() {
        YTVideoDetailsResponse yt = buildFullResponse();
        yt.getItems().get(0).setSnippet(null);

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result).isNull();
    }

    @Test
    void should_set_null_duration_when_contentDetails_is_absent() {
        YTVideoDetailsResponse yt = buildFullResponse();
        yt.getItems().get(0).setContentDetails(null);

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result).isNotNull();
        assertThat(result.getDuration()).isNull();
    }

    @Test
    void should_set_null_counts_when_statistics_is_absent() {
        YTVideoDetailsResponse yt = buildFullResponse();
        yt.getItems().get(0).setStatistics(null);

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result).isNotNull();
        assertThat(result.getViewCount()).isNull();
        assertThat(result.getLikeCount()).isNull();
    }

    // --- helper ---

    private YTVideoDetailsResponse buildFullResponse() {
        var thumbnail = new YTVideoDetailsResponse.Item.Snippet.Thumbnails.Thumbnail();
        thumbnail.setUrl("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");

        var thumbnails = new YTVideoDetailsResponse.Item.Snippet.Thumbnails();
        thumbnails.setHigh(thumbnail);

        var snippet = new YTVideoDetailsResponse.Item.Snippet();
        snippet.setPublishedAt("2009-10-25T06:57:33Z");
        snippet.setTitle("Never Gonna Give You Up");
        snippet.setDescription("The official music video.");
        snippet.setChannelTitle("RickAstleyVEVO");
        snippet.setThumbnails(thumbnails);

        var contentDetails = new YTVideoDetailsResponse.Item.ContentDetails();
        contentDetails.setDuration("PT3M33S");

        var statistics = new YTVideoDetailsResponse.Item.Statistics();
        statistics.setViewCount("1400000000");
        statistics.setLikeCount("15000000");

        var item = new YTVideoDetailsResponse.Item();
        item.setId("dQw4w9WgXcQ");
        item.setSnippet(snippet);
        item.setContentDetails(contentDetails);
        item.setStatistics(statistics);

        var yt = new YTVideoDetailsResponse();
        yt.setItems(List.of(item));
        return yt;
    }
}
