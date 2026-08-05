package com.sxxm.stockknock.youtube.repository;

import com.sxxm.stockknock.youtube.entity.VideoAnalysisStatus;
import com.sxxm.stockknock.youtube.entity.YoutubeVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface YoutubeVideoRepository extends JpaRepository<YoutubeVideo, Long> {

    boolean existsByVideoId(String videoId);

    Optional<YoutubeVideo> findByVideoId(String videoId);

    List<YoutubeVideo> findByAnalysisStatusIn(List<VideoAnalysisStatus> statuses);

    @Query("""
            SELECT v FROM YoutubeVideo v
            LEFT JOIN FETCH v.channel
            WHERE v.analysisStatus IN :statuses
            """)
    List<YoutubeVideo> findByAnalysisStatusInWithChannel(@Param("statuses") List<VideoAnalysisStatus> statuses);

    @Query("""
            SELECT v FROM YoutubeVideo v
            LEFT JOIN FETCH v.channel
            LEFT JOIN FETCH v.analysis
            WHERE v.publishedAt >= :since
              AND (:channelDbId IS NULL OR v.channel.id = :channelDbId)
              AND v.analysisStatus = com.sxxm.stockknock.youtube.entity.VideoAnalysisStatus.SUCCESS
            ORDER BY v.publishedAt DESC
            """)
    List<YoutubeVideo> findBriefings(
            @Param("since") LocalDateTime since,
            @Param("channelDbId") Long channelDbId);

    @Query("""
            SELECT v FROM YoutubeVideo v
            LEFT JOIN FETCH v.channel
            LEFT JOIN FETCH v.analysis
            WHERE v.id = :id
            """)
    Optional<YoutubeVideo> findByIdWithDetails(@Param("id") Long id);
}
