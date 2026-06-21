package com.example.youtubeplaylistapi.controller;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VideoController implements VideoApi {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @Override
    public ResponseEntity<SongDetail> getVideoDetails(String videoId) {
        log.info("endpoint=/youtube/song/{}", videoId);
        SongDetail songDetail = videoService.getVideo(videoId);
        log.info("endpoint=/youtube/song/{} status=200 videoFound=true", videoId);
        return ResponseEntity.ok(songDetail);
    }
}
