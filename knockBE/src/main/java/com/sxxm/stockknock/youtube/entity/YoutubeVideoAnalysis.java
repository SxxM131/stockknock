package com.sxxm.stockknock.youtube.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영상 AI 분석 결과 (One-to-One, video_id = PK).
 * 자막 원문은 저장하지 않고 요약/톤/종목만 보관.
 */
@Entity
@Table(name = "youtube_video_analyses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YoutubeVideoAnalysis {

    @Id
    @Column(name = "video_id")
    private Long videoId;

    @OneToOne
    @JoinColumn(name = "video_id", insertable = false, updatable = false)
    private YoutubeVideo video;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 언급 종목 심볼 JSON 배열 문자열 예: ["005930","AAPL"] */
    @Column(name = "key_stocks", columnDefinition = "TEXT")
    private String keyStocks;

    /** 단기 / 중기 / 장기 */
    @Column(name = "forecast_period", length = 20)
    private String forecastPeriod;

    /** positive / negative / neutral */
    @Column(length = 20)
    private String sentiment;

    @Column(name = "ai_comment", columnDefinition = "TEXT")
    private String aiComment;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onCreate() {
        if (analyzedAt == null) {
            analyzedAt = LocalDateTime.now();
        }
    }
}
