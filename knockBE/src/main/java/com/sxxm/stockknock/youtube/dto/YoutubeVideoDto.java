package com.sxxm.stockknock.youtube.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YoutubeVideoDto {
    private Long id;
    private String videoId;
    private String title;
    private String url;
    private LocalDateTime publishedAt;
    private String thumbnailUrl;
    private String channelName;
    private String channelYoutubeId;
    private String category;
    private String analysisStatus;
    private YoutubeVideoAnalysisDto analysis;
}
