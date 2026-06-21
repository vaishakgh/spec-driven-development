package com.example.youtubeplaylistapi.exception;

public class UpstreamUnauthorizedException extends RuntimeException {

    public UpstreamUnauthorizedException() {
        super("Invalid or missing YouTube API Key.");
    }
}
