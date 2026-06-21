package com.example.youtubeplaylistapi.exception;

public class UpstreamConnectivityException extends RuntimeException {

    public UpstreamConnectivityException() {
        super("Unable to connect to YouTube API.");
    }
}
