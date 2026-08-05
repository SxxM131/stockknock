package com.sxxm.stockknock.youtube.controller;

import com.sxxm.stockknock.youtube.dto.YoutubeChannelDto;
import com.sxxm.stockknock.youtube.dto.YoutubeVideoDto;
import com.sxxm.stockknock.youtube.service.YoutubeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/youtube")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class YoutubeController {

    private static final Logger log = LoggerFactory.getLogger(YoutubeController.class);

    private final YoutubeService youtubeService;

    public YoutubeController(YoutubeService youtubeService) {
        this.youtubeService = youtubeService;
    }

    /** 활성화된 채널 목록 (프론트 필터용) */
    @GetMapping("/channels")
    public ResponseEntity<List<YoutubeChannelDto>> getChannels() {
        return ResponseEntity.ok(youtubeService.getActiveChannels());
    }

    /**
     * 최근 N일 영상 요약 목록.
     * @param channelId DB PK (youtube_channels.id). 없으면 전체
     * @param days 기본 7
     */
    @GetMapping("/briefings")
    public ResponseEntity<List<YoutubeVideoDto>> getBriefings(
            @RequestParam(required = false) Long channelId,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(youtubeService.getBriefings(channelId, days));
    }

    /** 특정 영상 상세 분석 */
    @GetMapping("/briefings/{videoId}")
    public ResponseEntity<?> getBriefing(@PathVariable Long videoId) {
        try {
            return ResponseEntity.ok(youtubeService.getBriefingById(videoId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("[YouTube] 상세 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
