package com.sxxm.stockknock.youtube.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유튜브 영상 메타데이터.
 * 자막 원문은 저장하지 않는다 (저작권/비용).
 */
@Entity
@Table(name = "youtube_videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YoutubeVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", referencedColumnName = "id", nullable = false)
    private YoutubeChannel channel;

    /** YouTube 영상 ID (예: dQw4w9WgXcQ) */
    @Column(name = "video_id", nullable = false, unique = true, length = 32)
    private String videoId;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 20, nullable = false)
    @Builder.Default
    private VideoAnalysisStatus analysisStatus = VideoAnalysisStatus.PENDING;

    @OneToOne(mappedBy = "video", cascade = CascadeType.ALL)
    private YoutubeVideoAnalysis analysis;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (analysisStatus == null) {
            analysisStatus = VideoAnalysisStatus.PENDING;
        }
    }
}
