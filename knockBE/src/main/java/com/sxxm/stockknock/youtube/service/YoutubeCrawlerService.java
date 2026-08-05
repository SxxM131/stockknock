package com.sxxm.stockknock.youtube.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxxm.stockknock.youtube.entity.VideoAnalysisStatus;
import com.sxxm.stockknock.youtube.entity.YoutubeChannel;
import com.sxxm.stockknock.youtube.entity.YoutubeVideo;
import com.sxxm.stockknock.youtube.repository.YoutubeChannelRepository;
import com.sxxm.stockknock.youtube.repository.YoutubeVideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * YouTube Data API v3 로 화이트리스트 채널의 신규 영상을 수집한다.
 * 이미 저장된 video_id 는 스킵한다.
 */
@Service
public class YoutubeCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeCrawlerService.class);
    private static final int MAX_ITEMS_PER_CHANNEL = 5;

    private final YoutubeChannelRepository channelRepository;
    private final YoutubeVideoRepository videoRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${youtube.api.key:}")
    private String youtubeApiKey;

    public YoutubeCrawlerService(
            YoutubeChannelRepository channelRepository,
            YoutubeVideoRepository videoRepository,
            ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    /**
     * 활성 채널의 최신 영상을 수집하고 신규 건수를 반환한다.
     */
    @Transactional
    public int crawlActiveChannels() {
        if (youtubeApiKey == null || youtubeApiKey.isBlank()) {
            log.warn("[YouTube Crawler] YOUTUBE_API_KEY 미설정 — 수집 스킵");
            return 0;
        }

        List<YoutubeChannel> channels = channelRepository.findByIsActiveTrueOrderByChannelNameAsc();
        int saved = 0;
        for (YoutubeChannel channel : channels) {
            if (channel.getChannelId() == null || channel.getChannelId().startsWith("PLACEHOLDER_")) {
                log.info("[YouTube Crawler] 플레이스홀더 채널 스킵: {}", channel.getChannelName());
                continue;
            }
            try {
                saved += crawlChannel(channel);
            } catch (Exception e) {
                log.error("[YouTube Crawler] 채널 수집 실패 channel={}: {}",
                        channel.getChannelName(), e.getMessage(), e);
            }
        }
        log.info("[YouTube Crawler] 신규 영상 {}건 저장", saved);
        return saved;
    }

    private int crawlChannel(YoutubeChannel channel) throws Exception {
        String uploadsPlaylistId = fetchUploadsPlaylistId(channel.getChannelId());
        if (uploadsPlaylistId == null || uploadsPlaylistId.isBlank()) {
            log.warn("[YouTube Crawler] uploads playlist 없음: {}", channel.getChannelId());
            return 0;
        }

        String url = UriComponentsBuilder
                .fromHttpUrl("https://www.googleapis.com/youtube/v3/playlistItems")
                .queryParam("part", "snippet,contentDetails")
                .queryParam("playlistId", uploadsPlaylistId)
                .queryParam("maxResults", MAX_ITEMS_PER_CHANNEL)
                .queryParam("key", youtubeApiKey)
                .build(true)
                .toUriString();

        String body = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        if (body == null || body.isBlank()) {
            return 0;
        }

        JsonNode items = objectMapper.readTree(body).path("items");
        if (!items.isArray()) {
            return 0;
        }

        int saved = 0;
        for (JsonNode item : items) {
            String youtubeVideoId = item.path("contentDetails").path("videoId").asText(null);
            if (youtubeVideoId == null || youtubeVideoId.isBlank()) {
                youtubeVideoId = item.path("snippet").path("resourceId").path("videoId").asText(null);
            }
            if (youtubeVideoId == null || youtubeVideoId.isBlank()) {
                continue;
            }
            if (videoRepository.existsByVideoId(youtubeVideoId)) {
                continue;
            }

            JsonNode snippet = item.path("snippet");
            String title = snippet.path("title").asText("");
            String published = snippet.path("publishedAt").asText(null);
            String thumb = snippet.path("thumbnails").path("medium").path("url").asText(null);
            if (thumb == null || thumb.isBlank()) {
                thumb = snippet.path("thumbnails").path("default").path("url").asText(null);
            }

            YoutubeVideo video = YoutubeVideo.builder()
                    .channel(channel)
                    .videoId(youtubeVideoId)
                    .title(title)
                    .url("https://www.youtube.com/watch?v=" + youtubeVideoId)
                    .publishedAt(parsePublishedAt(published))
                    .thumbnailUrl(thumb)
                    .analysisStatus(VideoAnalysisStatus.PENDING)
                    .build();
            videoRepository.save(video);
            saved++;
            log.info("[YouTube Crawler] 신규 영상 저장: {} ({})", title, youtubeVideoId);
        }
        return saved;
    }

    private String fetchUploadsPlaylistId(String channelId) throws Exception {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://www.googleapis.com/youtube/v3/channels")
                .queryParam("part", "contentDetails")
                .queryParam("id", channelId)
                .queryParam("key", youtubeApiKey)
                .build(true)
                .toUriString();

        String body = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                .block();

        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode items = objectMapper.readTree(body).path("items");
        if (!items.isArray() || items.isEmpty()) {
            return null;
        }
        return items.get(0)
                .path("contentDetails")
                .path("relatedPlaylists")
                .path("uploads")
                .asText(null);
    }

    private LocalDateTime parsePublishedAt(String iso) {
        if (iso == null || iso.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(iso), ZoneId.of("Asia/Seoul"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
