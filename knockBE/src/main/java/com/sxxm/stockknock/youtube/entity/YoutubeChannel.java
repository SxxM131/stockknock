package com.sxxm.stockknock.youtube.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "youtube_channels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YoutubeChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** YouTube 채널 고유 ID (예: UCxxxx) */
    @Column(name = "channel_id", nullable = false, unique = true, length = 64)
    private String channelId;

    @Column(name = "channel_name", nullable = false, length = 200)
    private String channelName;

    /** 예: 국내주식 / 미국주식 / 거시경제 */
    @Column(length = 50)
    private String category;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }
}
