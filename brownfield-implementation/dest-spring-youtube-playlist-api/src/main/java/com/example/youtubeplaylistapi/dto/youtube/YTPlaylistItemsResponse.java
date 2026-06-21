package com.example.youtubeplaylistapi.dto.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YTPlaylistItemsResponse {

    private String nextPageToken;
    private PageInfo pageInfo;
    private List<Item> items;

    public String getNextPageToken() {
        return nextPageToken;
    }

    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }

    public PageInfo getPageInfo() {
        return pageInfo;
    }

    public void setPageInfo(PageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private Integer totalResults;
        private Integer resultsPerPage;

        public Integer getTotalResults() {
            return totalResults;
        }

        public void setTotalResults(Integer totalResults) {
            this.totalResults = totalResults;
        }

        public Integer getResultsPerPage() {
            return resultsPerPage;
        }

        public void setResultsPerPage(Integer resultsPerPage) {
            this.resultsPerPage = resultsPerPage;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Snippet snippet;

        public Snippet getSnippet() {
            return snippet;
        }

        public void setSnippet(Snippet snippet) {
            this.snippet = snippet;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Snippet {
            private String title;
            private String description;
            private String publishedAt;
            private Integer position;
            private Thumbnails thumbnails;
            private ResourceId resourceId;

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }

            public String getDescription() {
                return description;
            }

            public void setDescription(String description) {
                this.description = description;
            }

            public String getPublishedAt() {
                return publishedAt;
            }

            public void setPublishedAt(String publishedAt) {
                this.publishedAt = publishedAt;
            }

            public Integer getPosition() {
                return position;
            }

            public void setPosition(Integer position) {
                this.position = position;
            }

            public Thumbnails getThumbnails() {
                return thumbnails;
            }

            public void setThumbnails(Thumbnails thumbnails) {
                this.thumbnails = thumbnails;
            }

            public ResourceId getResourceId() {
                return resourceId;
            }

            public void setResourceId(ResourceId resourceId) {
                this.resourceId = resourceId;
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Thumbnails {
                private Thumbnail medium;

                public Thumbnail getMedium() {
                    return medium;
                }

                public void setMedium(Thumbnail medium) {
                    this.medium = medium;
                }

                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class Thumbnail {
                    private String url;

                    public String getUrl() {
                        return url;
                    }

                    public void setUrl(String url) {
                        this.url = url;
                    }
                }
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class ResourceId {
                private String videoId;

                public String getVideoId() {
                    return videoId;
                }

                public void setVideoId(String videoId) {
                    this.videoId = videoId;
                }
            }
        }
    }
}
