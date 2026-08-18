package com.jobharvest.repository;

import com.jobharvest.ingestion.IngestionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngestionLogRepository extends JpaRepository<IngestionLog, Long> {

    Optional<IngestionLog> findTopBySourceOrderByStartedAtDesc(String source);

    List<IngestionLog> findTop10ByOrderByStartedAtDesc();
}
