package com.sxxm.stockknock.youtube.repository;

import com.sxxm.stockknock.youtube.entity.YoutubeVideoAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface YoutubeVideoAnalysisRepository extends JpaRepository<YoutubeVideoAnalysis, Long> {

    Optional<YoutubeVideoAnalysis> findByVideoId(Long videoId);

    boolean existsByVideoId(Long videoId);
}
