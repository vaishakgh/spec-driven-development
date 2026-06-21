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

        SongDetail detail = new SongDetail();
        detail.setVideoId(item.getId());                         // item-level id
        detail.setTitle(snippet.getTitle());
        detail.setDescription(snippet.getDescription());
        detail.setChannelName(snippet.getChannelTitle());        // channelTitle → channelName
        detail.setPublishedAt(snippet.getPublishedAt());         // pass-through, no reformatting
        detail.setDuration(item.getContentDetails().getDuration());
        detail.setViewCount(item.getStatistics().getViewCount());
        detail.setLikeCount(item.getStatistics().getLikeCount());

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
