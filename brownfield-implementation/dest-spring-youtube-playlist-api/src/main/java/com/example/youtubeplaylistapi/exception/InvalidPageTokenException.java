package com.example.youtubeplaylistapi.exception;

public class InvalidPageTokenException extends RuntimeException {
    public InvalidPageTokenException() {
        super("Invalid or malformed pageToken.");
    }
}
