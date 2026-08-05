package com.sxxm.stockknock.youtube.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자막 추출 서비스.
 * 1) YouTube timedtext(공개 자막) 시도
 * 2) 실패 시 videos.list description 폴백
 * 원문 자막은 DB에 저장하지 않고, 호출 측에서 GPT 입력으로만 사용한다.
 */
@Service
public class YoutubeTranscriptService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeTranscriptService.class);
    private static final Pattern CAPTION_TEXT = Pattern.compile("<text[^>]*>(.*?)</text>", Pattern.DOTALL);
    private static final int MAX_SOURCE_CHARS = 12000;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${youtube.api.key:}")
    private String youtubeApiKey;

    public YoutubeTranscriptService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * 분석용 텍스트 확보 (자막 우선, 설명 폴백).
     * @return empty면 스킵 대상
     */
    public Optional<String> extractSourceText(String youtubeVideoId) {
        Optional<String> transcript = fetchTimedText(youtubeVideoId, "ko")
                .or(() -> fetchTimedText(youtubeVideoId, "en"))
                .or(() -> fetchTimedText(youtubeVideoId, "en-US"));

        if (transcript.isPresent() && !transcript.get().isBlank()) {
            return Optional.of(truncate(transcript.get()));
        }

        Optional<String> description = fetchDescription(youtubeVideoId);
        if (description.isPresent() && description.get().trim().length() >= 40) {
            log.info("[YouTube] videoId={} 자막 없음 → description 폴백", youtubeVideoId);
            return Optional.of(truncate(description.get()));
        }

        log.warn("[YouTube] videoId={} 자막/설명 모두 없음 → 스킵", youtubeVideoId);
        return Optional.empty();
    }

    private Optional<String> fetchTimedText(String videoId, String lang) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://www.youtube.com/api/timedtext")
                    .queryParam("v", videoId)
                    .queryParam("lang", lang)
                    .build(true)
                    .toUriString();

            String xml = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .onErrorReturn("")
                    .block();

            if (xml == null || xml.isBlank() || !xml.contains("<text")) {
                return Optional.empty();
            }

            StringBuilder sb = new StringBuilder();
            Matcher matcher = CAPTION_TEXT.matcher(xml);
            while (matcher.find()) {
                String chunk = matcher.group(1)
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("\n", " ");
                try {
                    chunk = URLDecoder.decode(chunk, StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    // keep as-is
                }
                if (!chunk.isBlank()) {
                    sb.append(chunk).append(' ');
                }
            }
            String text = sb.toString().trim();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.debug("[YouTube] timedtext 실패 videoId={} lang={}: {}", videoId, lang, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fetchDescription(String videoId) {
        if (youtubeApiKey == null || youtubeApiKey.isBlank()) {
            log.warn("[YouTube] YOUTUBE_API_KEY 미설정 — description 조회 불가");
            return Optional.empty();
        }
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://www.googleapis.com/youtube/v3/videos")
                    .queryParam("part", "snippet")
                    .queryParam("id", videoId)
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
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return Optional.empty();
            }
            String description = items.get(0).path("snippet").path("description").asText("");
            return description.isBlank() ? Optional.empty() : Optional.of(description);
        } catch (Exception e) {
            log.warn("[YouTube] description 조회 실패 videoId={}: {}", videoId, e.getMessage());
            return Optional.empty();
        }
    }

    private String truncate(String text) {
        if (text.length() <= MAX_SOURCE_CHARS) {
            return text;
        }
        return text.substring(0, MAX_SOURCE_CHARS);
    }
}
