package com.sxxm.stockknock.youtube.repository;

import com.sxxm.stockknock.youtube.entity.YoutubeChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YoutubeChannelRepository extends JpaRepository<YoutubeChannel, Long> {

    List<YoutubeChannel> findByIsActiveTrueOrderByChannelNameAsc();

    Optional<YoutubeChannel> findByChannelId(String channelId);

    boolean existsByChannelId(String channelId);
}
