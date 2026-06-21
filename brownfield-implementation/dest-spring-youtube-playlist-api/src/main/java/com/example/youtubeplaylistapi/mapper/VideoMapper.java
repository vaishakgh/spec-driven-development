package com.example.youtubeplaylistapi.mapper;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.dto.youtube.YTVideoDetailsResponse;
import org.springframework.stereotype.Component;

@Component
public class VideoMapper {

    public SongDetail toSongDetail(YTVideoDetailsResponse ytResponse) {
        // Return null → VideoService will throw VideoNotFoundException → HTTP 404
        if (ytResponse == null || ytResponse.getItems() == null || ytResponse.getItems().isEmpty()) {
            return null;
        }

        var item = ytResponse.getItems().get(0);
        var snippet = item.getSnippet();
        // Guard: treat absent snippet as no usable video data → VideoService will throw VideoNotFoundException
        if (snippet == null) {
            return null;
        }

        SongDetail detail = new SongDetail();
        detail.setVideoId(item.getId());                         // item-level id
        detail.setTitle(snippet.getTitle());
        detail.setDescription(snippet.getDescription());
        detail.setChannelName(snippet.getChannelTitle());        // channelTitle → channelName
        detail.setPublishedAt(snippet.getPublishedAt());         // pass-through, no reformatting
        detail.setDuration(item.getContentDetails() != null ? item.getContentDetails().getDuration() : null);
        detail.setViewCount(item.getStatistics() != null ? item.getStatistics().getViewCount() : null);
        detail.setLikeCount(item.getStatistics() != null ? item.getStatistics().getLikeCount() : null);

        // high-quality thumbnail (nullable)
        String thumbnail = null;
        if (snippet.getThumbnails() != null && snippet.getThumbnails().getHigh() != null) {
            thumbnail = snippet.getThumbnails().getHigh().getUrl();
        }
        detail.setThumbnail(thumbnail);

        detail.setVideoUrl("https://www.youtube.com/watch?v=" + item.getId());

        return detail;
    }
}
