package com.example.youtubeplaylistapi.exception;

public class VideoNotFoundException extends RuntimeException {

    public VideoNotFoundException() {
        super("Video not found for the given videoId.");
    }
}
