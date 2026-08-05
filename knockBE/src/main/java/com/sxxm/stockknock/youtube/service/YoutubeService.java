package com.sxxm.stockknock.youtube.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxxm.stockknock.ai.dto.AIRequestOptions;
import com.sxxm.stockknock.ai.dto.AIResponseResult;
import com.sxxm.stockknock.ai.service.GPTClientService;
import com.sxxm.stockknock.youtube.dto.YoutubeChannelCreateRequest;
import com.sxxm.stockknock.youtube.dto.YoutubeChannelDto;
import com.sxxm.stockknock.youtube.dto.YoutubeVideoAnalysisDto;
import com.sxxm.stockknock.youtube.dto.YoutubeVideoDto;
import com.sxxm.stockknock.youtube.entity.VideoAnalysisStatus;
import com.sxxm.stockknock.youtube.entity.YoutubeChannel;
import com.sxxm.stockknock.youtube.entity.YoutubeVideo;
import com.sxxm.stockknock.youtube.entity.YoutubeVideoAnalysis;
import com.sxxm.stockknock.youtube.repository.YoutubeChannelRepository;
import com.sxxm.stockknock.youtube.repository.YoutubeVideoAnalysisRepository;
import com.sxxm.stockknock.youtube.repository.YoutubeVideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 유튜버 브리핑 조회/분석/채널 관리.
 * GPT 호출은 신규(또는 FAILED) 영상 1건당 1회만 수행하고 결과는 DB에 캐시한다.
 * 자막 원문은 저장하지 않는다.
 */
@Service
public class YoutubeService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeService.class);

    private final YoutubeChannelRepository channelRepository;
    private final YoutubeVideoRepository videoRepository;
    private final YoutubeVideoAnalysisRepository analysisRepository;
    private final YoutubeTranscriptService transcriptService;
    private final GPTClientService gptClientService;
    private final ObjectMapper objectMapper;

    public YoutubeService(
            YoutubeChannelRepository channelRepository,
            YoutubeVideoRepository videoRepository,
            YoutubeVideoAnalysisRepository analysisRepository,
            YoutubeTranscriptService transcriptService,
            GPTClientService gptClientService,
            ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.analysisRepository = analysisRepository;
        this.transcriptService = transcriptService;
        this.gptClientService = gptClientService;
        this.objectMapper = objectMapper;
    }

    public List<YoutubeChannelDto> getActiveChannels() {
        return channelRepository.findByIsActiveTrueOrderByChannelNameAsc().stream()
                .map(this::toChannelDto)
                .toList();
    }

    public List<YoutubeVideoDto> getBriefings(Long channelDbId, int days) {
        int safeDays = days <= 0 ? 7 : days;
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);
        return videoRepository.findBriefings(since, channelDbId).stream()
                .map(this::toVideoDto)
                .toList();
    }

    public YoutubeVideoDto getBriefingById(Long videoDbId) {
        YoutubeVideo video = videoRepository.findByIdWithDetails(videoDbId)
                .orElseThrow(() -> new IllegalArgumentException("영상을 찾을 수 없습니다: " + videoDbId));
        return toVideoDto(video);
    }

    @Transactional
    public YoutubeChannelDto addChannel(YoutubeChannelCreateRequest request) {
        if (request.getChannelId() == null || request.getChannelId().isBlank()) {
            throw new IllegalArgumentException("channelId는 필수입니다.");
        }
        if (request.getChannelName() == null || request.getChannelName().isBlank()) {
            throw new IllegalArgumentException("channelName은 필수입니다.");
        }
        if (channelRepository.existsByChannelId(request.getChannelId().trim())) {
            throw new IllegalArgumentException("이미 등록된 채널입니다: " + request.getChannelId());
        }

        YoutubeChannel channel = YoutubeChannel.builder()
                .channelId(request.getChannelId().trim())
                .channelName(request.getChannelName().trim())
                .category(request.getCategory())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        return toChannelDto(channelRepository.save(channel));
    }

    /**
     * PENDING / FAILED 영상을 분석한다. 이미 SUCCESS인 영상은 재분석하지 않는다.
     */
    @Transactional
    public void analyzePendingAndFailedVideos() {
        List<YoutubeVideo> targets = videoRepository.findByAnalysisStatusInWithChannel(
                List.of(VideoAnalysisStatus.PENDING, VideoAnalysisStatus.FAILED));

        for (YoutubeVideo video : targets) {
            try {
                analyzeOne(video);
            } catch (Exception e) {
                log.error("[YouTube] 분석 중 예외 videoId={}: {}", video.getVideoId(), e.getMessage(), e);
                video.setAnalysisStatus(VideoAnalysisStatus.FAILED);
                videoRepository.save(video);
            }
        }
    }

    private void analyzeOne(YoutubeVideo video) {
        if (analysisRepository.existsByVideoId(video.getId())) {
            video.setAnalysisStatus(VideoAnalysisStatus.SUCCESS);
            videoRepository.save(video);
            return;
        }

        Optional<String> sourceText = transcriptService.extractSourceText(video.getVideoId());
        if (sourceText.isEmpty()) {
            video.setAnalysisStatus(VideoAnalysisStatus.SKIPPED);
            videoRepository.save(video);
            return;
        }

        String prompt = buildAnalysisPrompt(video, sourceText.get());
        AIRequestOptions options = AIRequestOptions.builder()
                .systemPrompt("당신은 한국 개인 투자자를 돕는 금융 콘텐츠 분석가입니다. "
                        + "반드시 지정된 JSON 형식만 출력하세요. 마크다운 코드블록을 쓰지 마세요.")
                .temperature(0.3)
                .maxTokens(1200)
                .timeoutSeconds(90)
                .build();

        AIResponseResult result = gptClientService.generateResponseAsync(prompt, options)
                .block(java.time.Duration.ofSeconds(95));

        if (result == null || !result.isSuccess()) {
            log.warn("[YouTube] GPT 실패 videoId={}: {}",
                    video.getVideoId(),
                    result != null ? result.getErrorMessage() : "null result");
            video.setAnalysisStatus(VideoAnalysisStatus.FAILED);
            videoRepository.save(video);
            return;
        }

        ParsedAnalysis parsed = parseGptJson(result.getContent());
        YoutubeVideoAnalysis analysis = YoutubeVideoAnalysis.builder()
                .videoId(video.getId())
                .summary(parsed.summary)
                .keyStocks(parsed.keyStocksJson)
                .forecastPeriod(parsed.forecastPeriod)
                .sentiment(parsed.sentiment)
                .aiComment(parsed.aiComment)
                .analyzedAt(LocalDateTime.now())
                .build();
        analysisRepository.save(analysis);

        video.setAnalysisStatus(VideoAnalysisStatus.SUCCESS);
        videoRepository.save(video);
        log.info("[YouTube] 분석 완료: {} ({})", video.getTitle(), video.getVideoId());
    }

    private String buildAnalysisPrompt(YoutubeVideo video, String sourceText) {
        String channelName = video.getChannel() != null ? video.getChannel().getChannelName() : "알 수 없음";
        return """
                아래는 유튜브 투자 채널 영상의 자막 또는 설명입니다.
                JSON만 출력하세요. 키:
                - summary: 2~3문장 한국어 요약
                - key_stocks: 언급 종목 심볼 배열 (한국 종목은 6자리 코드, 미국은 티커). 없으면 []
                - forecast_period: "단기" | "중기" | "장기" 중 하나
                - sentiment: "positive" | "negative" | "neutral"
                - ai_comment: 투자자 관점 한 줄 코멘트

                채널: %s
                제목: %s
                본문:
                %s
                """.formatted(channelName, video.getTitle(), sourceText);
    }

    private ParsedAnalysis parseGptJson(String raw) {
        ParsedAnalysis fallback = new ParsedAnalysis(
                raw != null && raw.length() > 400 ? raw.substring(0, 400) : (raw != null ? raw : ""),
                "[]",
                "중기",
                "neutral",
                "분석 결과를 구조화하지 못했습니다.");
        try {
            String json = extractJsonObject(raw);
            JsonNode node = objectMapper.readTree(json);
            String summary = node.path("summary").asText(fallback.summary);
            String forecast = node.path("forecast_period").asText("중기");
            String sentiment = normalizeSentiment(node.path("sentiment").asText("neutral"));
            String aiComment = node.path("ai_comment").asText("");

            String keyStocksJson = "[]";
            JsonNode stocks = node.path("key_stocks");
            if (stocks.isArray()) {
                keyStocksJson = objectMapper.writeValueAsString(stocks);
            } else if (stocks.isTextual() && !stocks.asText().isBlank()) {
                keyStocksJson = objectMapper.writeValueAsString(List.of(stocks.asText()));
            }

            return new ParsedAnalysis(summary, keyStocksJson, forecast, sentiment, aiComment);
        } catch (Exception e) {
            log.warn("[YouTube] GPT JSON 파싱 실패: {}", e.getMessage());
            return fallback;
        }
    }

    private String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String normalizeSentiment(String value) {
        if (value == null) {
            return "neutral";
        }
        String v = value.trim().toLowerCase();
        if (v.contains("pos") || v.contains("긍정")) {
            return "positive";
        }
        if (v.contains("neg") || v.contains("부정")) {
            return "negative";
        }
        return "neutral";
    }

    private YoutubeChannelDto toChannelDto(YoutubeChannel channel) {
        return YoutubeChannelDto.builder()
                .id(channel.getId())
                .channelId(channel.getChannelId())
                .channelName(channel.getChannelName())
                .category(channel.getCategory())
                .isActive(channel.getIsActive())
                .build();
    }

    private YoutubeVideoDto toVideoDto(YoutubeVideo video) {
        YoutubeVideoAnalysisDto analysisDto = null;
        if (video.getAnalysis() != null) {
            analysisDto = YoutubeVideoAnalysisDto.builder()
                    .summary(video.getAnalysis().getSummary())
                    .keyStocks(parseKeyStocks(video.getAnalysis().getKeyStocks()))
                    .forecastPeriod(video.getAnalysis().getForecastPeriod())
                    .sentiment(video.getAnalysis().getSentiment())
                    .aiComment(video.getAnalysis().getAiComment())
                    .analyzedAt(video.getAnalysis().getAnalyzedAt())
                    .build();
        }

        return YoutubeVideoDto.builder()
                .id(video.getId())
                .videoId(video.getVideoId())
                .title(video.getTitle())
                .url(video.getUrl())
                .publishedAt(video.getPublishedAt())
                .thumbnailUrl(video.getThumbnailUrl())
                .channelName(video.getChannel() != null ? video.getChannel().getChannelName() : null)
                .channelYoutubeId(video.getChannel() != null ? video.getChannel().getChannelId() : null)
                .category(video.getChannel() != null ? video.getChannel().getCategory() : null)
                .analysisStatus(video.getAnalysisStatus() != null ? video.getAnalysisStatus().name() : null)
                .analysis(analysisDto)
                .build();
    }

    private List<String> parseKeyStocks(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private record ParsedAnalysis(
            String summary,
            String keyStocksJson,
            String forecastPeriod,
            String sentiment,
            String aiComment) {
    }
}
