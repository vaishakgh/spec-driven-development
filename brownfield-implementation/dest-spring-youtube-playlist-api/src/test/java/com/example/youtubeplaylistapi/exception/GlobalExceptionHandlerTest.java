package com.example.youtubeplaylistapi.exception;

import com.example.youtubeplaylistapi.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest mockRequest = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() {
        when(mockRequest.getRequestURI()).thenReturn("/test-path");
    }

    @Test
    void should_return_404_when_route_not_found() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/unknown", null);
        ResponseEntity<ErrorResponse> resp = handler.handleNotFound(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(404);
        assertThat(resp.getBody().getError()).isEqualTo("Not Found");
        assertThat(resp.getBody().getMessage()).isEqualTo("The requested resource was not found.");
    }

    @Test
    void should_return_405_when_method_not_supported() {
        var ex = new HttpRequestMethodNotSupportedException("POST");
        ResponseEntity<ErrorResponse> resp = handler.handleMethodNotAllowed(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(405);
        assertThat(resp.getBody().getError()).isEqualTo("Method Not Allowed");
        assertThat(resp.getBody().getMessage()).isEqualTo("HTTP method not supported for this endpoint.");
    }

    @Test
    void should_return_406_when_media_type_not_acceptable() {
        var ex = new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<ErrorResponse> resp = handler.handleNotAcceptable(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(406);
        assertThat(resp.getBody().getError()).isEqualTo("Not Acceptable");
        assertThat(resp.getBody().getMessage()).isEqualTo("Requested media type is not supported.");
    }

    @Test
    void should_return_415_when_content_type_unsupported() {
        var ex = new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<ErrorResponse> resp = handler.handleUnsupportedMediaType(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(415);
        assertThat(resp.getBody().getError()).isEqualTo("Unsupported Media Type");
        assertThat(resp.getBody().getMessage()).isEqualTo("Content type not supported.");
    }

    @Test
    void should_return_400_when_argument_not_valid() {
        var ex = new org.springframework.web.bind.MissingServletRequestParameterException("pageToken", "String");
        ResponseEntity<ErrorResponse> resp = handler.handleBadRequest(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getError()).isEqualTo("Bad Request");
        assertThat(resp.getBody().getMessage()).isEqualTo("Request does not match API specification.");
    }

    // ── Story 4.2 tests ───────────────────────────────────────────────────────

    @Test
    void should_return_401_when_upstream_unauthorized() {
        var ex = new UpstreamUnauthorizedException();
        var resp = handler.handleUpstreamUnauthorized(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(401);
        assertThat(resp.getBody().getError()).isEqualTo("Unauthorized");
        assertThat(resp.getBody().getMessage()).isEqualTo("Invalid or missing YouTube API Key.");
    }

    @Test
    void should_return_503_when_upstream_connectivity_fails() {
        var ex = new WebClientRequestException(new RuntimeException("connection refused"),
                HttpMethod.GET, URI.create("https://googleapis.com"), HttpHeaders.EMPTY);
        var resp = handler.handleUpstreamConnectivity(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(503);
        assertThat(resp.getBody().getError()).isEqualTo("Connection Error");
        assertThat(resp.getBody().getMessage()).isEqualTo("Unable to connect to YouTube API.");
    }

    @Test
    void should_return_404_when_video_not_found() {
        var ex = new VideoNotFoundException();
        var resp = handler.handleVideoNotFound(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(404);
        assertThat(resp.getBody().getError()).isEqualTo("Not Found");
        assertThat(resp.getBody().getMessage()).isEqualTo("Video not found for the given videoId.");
    }

    @Test
    void should_return_500_for_unexpected_exception() {
        var ex = new RuntimeException("unexpected");
        var resp = handler.handleUnexpected(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(500);
        assertThat(resp.getBody().getError()).isEqualTo("Internal Server Error");
        assertThat(resp.getBody().getMessage()).isEqualTo("An unexpected error occurred.");
    }
}
