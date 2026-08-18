package com.jobharvest.ingestion;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ingestion_logs")
public class IngestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "total_fetched")
    private int totalFetched;

    @Column(name = "total_new")
    private int totalNew;

    @Column(name = "total_duplicates")
    private int totalDuplicates;

    @Column(name = "total_failed")
    private int totalFailed;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public int getTotalFetched() { return totalFetched; }
    public void setTotalFetched(int totalFetched) { this.totalFetched = totalFetched; }

    public int getTotalNew() { return totalNew; }
    public void setTotalNew(int totalNew) { this.totalNew = totalNew; }

    public int getTotalDuplicates() { return totalDuplicates; }
    public void setTotalDuplicates(int totalDuplicates) { this.totalDuplicates = totalDuplicates; }

    public int getTotalFailed() { return totalFailed; }
    public void setTotalFailed(int totalFailed) { this.totalFailed = totalFailed; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
