package com.example.youtubeplaylistapi.mapper;

import com.example.youtubeplaylistapi.dto.PlaylistItem;
import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.dto.youtube.YTPlaylistItemsResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlaylistMapper {

    public PlaylistResponse toPlaylistResponse(YTPlaylistItemsResponse ytResponse) {
        PlaylistResponse response = new PlaylistResponse();

        if (ytResponse.getPageInfo() != null) {
            response.setTotalResults(ytResponse.getPageInfo().getTotalResults());
            response.setResultsPerPage(ytResponse.getPageInfo().getResultsPerPage());
        } else {
            response.setTotalResults(0);
            response.setResultsPerPage(0);
        }

        response.setNextPageToken(ytResponse.getNextPageToken());

        List<PlaylistItem> items = new ArrayList<>();
        if (ytResponse.getItems() != null) {
            for (YTPlaylistItemsResponse.Item ytItem : ytResponse.getItems()) {
                items.add(toPlaylistItem(ytItem));
            }
        }
        response.setPlaylist(items);

        return response;
    }

    private PlaylistItem toPlaylistItem(YTPlaylistItemsResponse.Item ytItem) {
        var snippet = ytItem.getSnippet();
        if (snippet == null) {
            return new PlaylistItem();
        }
        PlaylistItem item = new PlaylistItem();
        item.setPosition(snippet.getPosition());
        String videoId = snippet.getResourceId() != null ? snippet.getResourceId().getVideoId() : null;
        item.setVideoId(videoId);
        item.setTitle(snippet.getTitle());
        item.setDescription(snippet.getDescription());
        item.setPublishedAt(snippet.getPublishedAt());

        String thumbnail = null;
        if (snippet.getThumbnails() != null && snippet.getThumbnails().getMedium() != null) {
            thumbnail = snippet.getThumbnails().getMedium().getUrl();
        }
        item.setThumbnail(thumbnail);

        item.setVideoUrl(videoId != null ? "https://www.youtube.com/watch?v=" + videoId : null);

        return item;
    }
}
