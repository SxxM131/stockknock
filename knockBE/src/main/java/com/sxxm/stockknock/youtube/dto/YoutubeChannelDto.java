package com.sxxm.stockknock.youtube.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YoutubeChannelDto {
    private Long id;
    private String channelId;
    private String channelName;
    private String category;
    private Boolean isActive;
}
