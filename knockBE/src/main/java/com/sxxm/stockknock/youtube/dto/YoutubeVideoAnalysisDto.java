package com.sxxm.stockknock.youtube.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YoutubeVideoAnalysisDto {
    private String summary;
    private List<String> keyStocks;
    private String forecastPeriod;
    private String sentiment;
    private String aiComment;
    private LocalDateTime analyzedAt;
}
