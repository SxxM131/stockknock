package com.sxxm.stockknock.youtube.scheduler;

import com.sxxm.stockknock.youtube.service.YoutubeCrawlerService;
import com.sxxm.stockknock.youtube.service.YoutubeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 유튜버 브리핑 수집·분석 스케줄러.
 * 하루 2회 (08:30, 15:30 KST 가정 — 서버 TZ 확인 필요).
 */
@Component
public class YoutubeCrawlerScheduler {

    private static final Logger log = LoggerFactory.getLogger(YoutubeCrawlerScheduler.class);

    private final YoutubeCrawlerService crawlerService;
    private final YoutubeService youtubeService;

    public YoutubeCrawlerScheduler(YoutubeCrawlerService crawlerService, YoutubeService youtubeService) {
        this.crawlerService = crawlerService;
        this.youtubeService = youtubeService;
    }

    @Scheduled(cron = "0 30 8 * * *")
    public void morningCrawl() {
        runPipeline("morning");
    }

    @Scheduled(cron = "0 30 15 * * *")
    public void afternoonCrawl() {
        runPipeline("afternoon");
    }

    private void runPipeline(String label) {
        log.info("[YouTube Scheduler] {} 파이프라인 시작", label);
        try {
            int saved = crawlerService.crawlActiveChannels();
            log.info("[YouTube Scheduler] {} 수집 완료 — 신규 {}건", label, saved);
            youtubeService.analyzePendingAndFailedVideos();
            log.info("[YouTube Scheduler] {} 분석 완료", label);
        } catch (Exception e) {
            log.error("[YouTube Scheduler] {} 파이프라인 실패: {}", label, e.getMessage(), e);
        }
    }
}
