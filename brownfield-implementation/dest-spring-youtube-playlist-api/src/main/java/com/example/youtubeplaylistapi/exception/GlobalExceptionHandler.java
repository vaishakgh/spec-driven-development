package com.example.youtubeplaylistapi.exception;

import com.example.youtubeplaylistapi.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Story 4.1 handlers — request-level errors

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 404, ex.getClass().getSimpleName());
        ErrorResponse body = new ErrorResponse();
        body.setError("Not Found");
        body.setMessage("The requested resource was not found.");
        body.setCode(404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 405, ex.getClass().getSimpleName());
        ErrorResponse body = new ErrorResponse();
        body.setError("Method Not Allowed");
        body.setMessage("HTTP method not supported for this endpoint.");
        body.setCode(405);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 406, ex.getClass().getSimpleName());
        ErrorResponse body = new ErrorResponse();
        body.setError("Not Acceptable");
        body.setMessage("Requested media type is not supported.");
        body.setCode(406);
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 415, ex.getClass().getSimpleName());
        ErrorResponse body = new ErrorResponse();
        body.setError("Unsupported Media Type");
        body.setMessage("Content type not supported.");
        body.setCode(415);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 400, ex.getClass().getSimpleName());
        ErrorResponse body = new ErrorResponse();
        body.setError("Bad Request");
        body.setMessage("Request does not match API specification.");
        body.setCode(400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Story 4.2 handlers — upstream and catch-all errors

    @ExceptionHandler(UpstreamUnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamUnauthorized(
            UpstreamUnauthorizedException ex, HttpServletRequest request) {
        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 401, ex.getClass().getSimpleName());
        ErrorResponse body = new ErrorResponse();
        body.setError("Unauthorized");
        body.setMessage("Invalid or missing YouTube API Key.");
        body.setCode(401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamConnectivity(
            WebClientRequestException ex, HttpServletRequest request) {
        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 503, ex.getClass().getSimpleName());
        ErrorResponse body = new ErrorResponse();
        body.setError("Connection Error");
        body.setMessage("Unable to connect to YouTube API.");
        body.setCode(503);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(VideoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVideoNotFound(
            VideoNotFoundException ex, HttpServletRequest request) {
        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 404, ex.getClass().getSimpleName());
        ErrorResponse body = new ErrorResponse();
        body.setError("Not Found");
        body.setMessage("Video not found for the given videoId.");
        body.setCode(404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Story 2.3 handler — pageToken format validation failure

    @ExceptionHandler(InvalidPageTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPageToken(
            InvalidPageTokenException ex, HttpServletRequest request) {

        log.warn("endpoint={} status={} error={}", request.getRequestURI(), 400,
                 ex.getClass().getSimpleName());

        ErrorResponse body = new ErrorResponse();
        body.setError("Bad Request");
        body.setMessage("Invalid or malformed pageToken.");
        body.setCode(400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("endpoint={} error={} message={}", request.getRequestURI(),
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
        ErrorResponse body = new ErrorResponse();
        body.setError("Internal Server Error");
        body.setMessage("An unexpected error occurred.");
        body.setCode(500);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
