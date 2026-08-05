package com.sxxm.stockknock.youtube.controller;

import com.sxxm.stockknock.youtube.dto.YoutubeChannelCreateRequest;
import com.sxxm.stockknock.youtube.dto.YoutubeChannelDto;
import com.sxxm.stockknock.youtube.service.YoutubeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 유튜브 채널 화이트리스트 관리 API.
 * TODO: 관리자 권한 체크 추가 예정 (현재는 인증된 사용자만 접근 가능).
 */
@RestController
@RequestMapping("/api/admin/youtube")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class YoutubeAdminController {

    private static final Logger log = LoggerFactory.getLogger(YoutubeAdminController.class);

    private final YoutubeService youtubeService;

    public YoutubeAdminController(YoutubeService youtubeService) {
        this.youtubeService = youtubeService;
    }

    @PostMapping("/channels")
    public ResponseEntity<?> addChannel(@RequestBody YoutubeChannelCreateRequest request) {
        try {
            YoutubeChannelDto created = youtubeService.addChannel(request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[YouTube Admin] 채널 추가 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "채널 추가 중 오류가 발생했습니다."));
        }
    }
}
