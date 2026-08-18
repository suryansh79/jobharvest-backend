package com.jobharvest.ingestion;

import com.jobharvest.config.IngestionProperties;
import com.jobharvest.model.Job;
import com.jobharvest.parser.JobNormalizer;
import com.jobharvest.repository.IngestionLogRepository;
import com.jobharvest.repository.JobRepository;
import com.jobharvest.source.JobSource;
import com.jobharvest.source.RawJobData;
import com.jobharvest.source.SourceFetchException;
import com.jobharvest.validation.JobValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    private JobSource jobSource;
    private JobNormalizer normalizer;
    private JobValidator validator;
    private JobRepository jobRepository;
    private IngestionLogRepository logRepository;
    private IngestionProperties properties;
    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        jobSource = mock(JobSource.class);
        normalizer = new JobNormalizer();
        validator = new JobValidator();
        jobRepository = mock(JobRepository.class);
        logRepository = mock(IngestionLogRepository.class);
        properties = new IngestionProperties();
        properties.setCooldownMinutes(60);

        when(jobSource.getSourceName()).thenReturn("jobicy");

        ingestionService = new IngestionService(
                jobSource, normalizer, validator, jobRepository, logRepository, properties
        );
    }

    @Test
    @DisplayName("Successful ingestion creates jobs and logs SUCCESS status")
    void testSuccessfulIngestion() {
        when(logRepository.findTopBySourceOrderByStartedAtDesc("jobicy")).thenReturn(Optional.empty());

        RawJobData raw = new RawJobData(
                101, "https://jobicy.com/101", "Developer", "Acme",
                List.of("IT"), List.of("Full-Time"), "Remote", "Mid",
                "Excerpt", "Description", "2026-08-18T00:00:00Z",
                50000, 70000, "USD", "yearly"
        );
        when(jobSource.fetchJobs()).thenReturn(List.of(raw));
        when(jobRepository.existsBySourceAndExternalId("jobicy", 101)).thenReturn(false);

        IngestionResult result = ingestionService.runIngestion();

        assertEquals("SUCCESS", result.status());
        assertEquals(1, result.totalFetched());
        assertEquals(1, result.totalNew());
        assertEquals(0, result.totalDuplicates());
        assertEquals(0, result.totalFailed());

        verify(jobRepository, times(1)).save(any(Job.class));
        verify(logRepository, times(2)).save(any(IngestionLog.class)); // 1 for RUNNING, 1 for SUCCESS
    }

    @Test
    @DisplayName("Cooldown active returns RATE_LIMITED without invoking JobSource")
    void testCooldownActive() {
        IngestionLog recentLog = new IngestionLog();
        recentLog.setStartedAt(Instant.now().minusSeconds(10 * 60)); // 10 mins ago (cooldown is 60)
        when(logRepository.findTopBySourceOrderByStartedAtDesc("jobicy")).thenReturn(Optional.of(recentLog));

        IngestionResult result = ingestionService.runIngestion();

        assertEquals("RATE_LIMITED", result.status());
        assertNotNull(result.nextAllowedAt());
        verify(jobSource, never()).fetchJobs();
    }

    @Test
    @DisplayName("Empty source response logs status as EMPTY")
    void testEmptySourceResponse() {
        when(logRepository.findTopBySourceOrderByStartedAtDesc("jobicy")).thenReturn(Optional.empty());
        when(jobSource.fetchJobs()).thenReturn(List.of());

        IngestionResult result = ingestionService.runIngestion();

        assertEquals("EMPTY", result.status());
        assertEquals(0, result.totalFetched());
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("Source fetch exception logs status as FAILED")
    void testSourceFetchFailure() {
        when(logRepository.findTopBySourceOrderByStartedAtDesc("jobicy")).thenReturn(Optional.empty());
        when(jobSource.fetchJobs()).thenThrow(new SourceFetchException("500 Server Error", 500, true));

        IngestionResult result = ingestionService.runIngestion();

        assertEquals("FAILED", result.status());
        assertEquals("500 Server Error", result.errorMessage());
    }
}
