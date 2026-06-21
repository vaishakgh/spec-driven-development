package com.example.youtubeplaylistapi.dto.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YTVideoDetailsResponse {

    private List<Item> items;

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        private String id;
        private Snippet snippet;
        private ContentDetails contentDetails;
        private Statistics statistics;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public Snippet getSnippet() { return snippet; }
        public void setSnippet(Snippet snippet) { this.snippet = snippet; }

        public ContentDetails getContentDetails() { return contentDetails; }
        public void setContentDetails(ContentDetails contentDetails) { this.contentDetails = contentDetails; }

        public Statistics getStatistics() { return statistics; }
        public void setStatistics(Statistics statistics) { this.statistics = statistics; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Snippet {

            private String publishedAt;
            private String title;
            private String description;
            private String channelTitle;
            private Thumbnails thumbnails;

            public String getPublishedAt() { return publishedAt; }
            public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

            public String getTitle() { return title; }
            public void setTitle(String title) { this.title = title; }

            public String getDescription() { return description; }
            public void setDescription(String description) { this.description = description; }

            public String getChannelTitle() { return channelTitle; }
            public void setChannelTitle(String channelTitle) { this.channelTitle = channelTitle; }

            public Thumbnails getThumbnails() { return thumbnails; }
            public void setThumbnails(Thumbnails thumbnails) { this.thumbnails = thumbnails; }

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Thumbnails {

                private Thumbnail high;

                public Thumbnail getHigh() { return high; }
                public void setHigh(Thumbnail high) { this.high = high; }

                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class Thumbnail {

                    private String url;

                    public String getUrl() { return url; }
                    public void setUrl(String url) { this.url = url; }
                }
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ContentDetails {

            private String duration;

            public String getDuration() { return duration; }
            public void setDuration(String duration) { this.duration = duration; }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Statistics {

            private String viewCount;
            private String likeCount;

            public String getViewCount() { return viewCount; }
            public void setViewCount(String viewCount) { this.viewCount = viewCount; }

            public String getLikeCount() { return likeCount; }
            public void setLikeCount(String likeCount) { this.likeCount = likeCount; }
        }
    }
}
