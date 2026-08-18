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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final JobSource jobSource;
    private final JobNormalizer normalizer;
    private final JobValidator validator;
    private final JobRepository jobRepository;
    private final IngestionLogRepository logRepository;
    private final IngestionProperties properties;

    private final AtomicBoolean ingestionInProgress = new AtomicBoolean(false);

    public IngestionService(JobSource jobSource,
                            JobNormalizer normalizer,
                            JobValidator validator,
                            JobRepository jobRepository,
                            IngestionLogRepository logRepository,
                            IngestionProperties properties) {
        this.jobSource = jobSource;
        this.normalizer = normalizer;
        this.validator = validator;
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
        this.properties = properties;
    }

    public IngestionResult runIngestion() {
        String sourceName = jobSource.getSourceName();
        int cooldownMinutes = properties.getCooldownMinutes();

        // 1. Check cooldown from database (survives restarts)
        Optional<IngestionLog> lastLog = logRepository.findTopBySourceOrderByStartedAtDesc(sourceName);
        if (lastLog.isPresent()) {
            Instant lastAttempt = lastLog.get().getStartedAt();
            Duration elapsed = Duration.between(lastAttempt, Instant.now());
            if (elapsed.toMinutes() < cooldownMinutes) {
                log.info("Cooldown active. Last attempt: {}, elapsed: {} min, required: {} min",
                        lastAttempt, elapsed.toMinutes(), cooldownMinutes);
                return IngestionResult.rateLimited(lastAttempt, cooldownMinutes);
            }
        }

        // 2. Acquire concurrency lock (non-blocking)
        if (!ingestionInProgress.compareAndSet(false, true)) {
            log.info("Ingestion already in progress, rejecting concurrent request");
            return IngestionResult.alreadyRunning();
        }

        try {
            // 3. Re-check cooldown under lock (double-check pattern)
            lastLog = logRepository.findTopBySourceOrderByStartedAtDesc(sourceName);
            if (lastLog.isPresent()) {
                Instant lastAttempt = lastLog.get().getStartedAt();
                Duration elapsed = Duration.between(lastAttempt, Instant.now());
                if (elapsed.toMinutes() < cooldownMinutes) {
                    log.info("Cooldown active (double-check). Rejecting.");
                    return IngestionResult.rateLimited(lastAttempt, cooldownMinutes);
                }
            }

            // 4. Record ingestion attempt in DB BEFORE fetching
            Instant startedAt = Instant.now();
            IngestionLog ingestionLog = new IngestionLog();
            ingestionLog.setSource(sourceName);
            ingestionLog.setStatus("RUNNING");
            ingestionLog.setStartedAt(startedAt);
            logRepository.save(ingestionLog);

            // 5. Execute ingestion
            return doIngestion(ingestionLog, startedAt);

        } finally {
            ingestionInProgress.set(false);
        }
    }

    private IngestionResult doIngestion(IngestionLog ingestionLog, Instant startedAt) {
        String sourceName = jobSource.getSourceName();
        int totalFetched = 0, totalNew = 0, totalDuplicates = 0, totalFailed = 0;

        try {
            // Fetch from source
            List<RawJobData> rawJobs = jobSource.fetchJobs();
            totalFetched = rawJobs.size();

            if (totalFetched == 0) {
                log.info("Source returned 0 jobs — recording as EMPTY");
                return completeIngestion(ingestionLog, startedAt, "EMPTY",
                        totalFetched, totalNew, totalDuplicates, totalFailed, null);
            }

            // Process each job
            for (RawJobData raw : rawJobs) {
                try {
                    Job job = normalizer.normalize(raw, sourceName);
                    JobValidator.ValidationResult validation = validator.validate(job);

                    if (!validation.valid()) {
                        log.warn("Job validation failed (externalId={}): {}",
                                raw.id(), validation.errors());
                        totalFailed++;
                        continue;
                    }

                    // Check deduplication
                    if (jobRepository.existsBySourceAndExternalId(sourceName, job.getExternalId())) {
                        totalDuplicates++;
                        continue;
                    }

                    // Persist
                    try {
                        jobRepository.save(job);
                        totalNew++;
                    } catch (DataIntegrityViolationException e) {
                        // Race condition: another thread inserted between check and save
                        totalDuplicates++;
                    }

                } catch (Exception e) {
                    log.warn("Error processing job (externalId={}): {}", raw.id(), e.getMessage());
                    totalFailed++;
                }
            }

            String status = determineStatus(totalFetched, totalNew, totalDuplicates, totalFailed);
            return completeIngestion(ingestionLog, startedAt, status,
                    totalFetched, totalNew, totalDuplicates, totalFailed, null);

        } catch (SourceFetchException e) {
            log.error("Source fetch failed: {}", e.getMessage());
            return completeIngestion(ingestionLog, startedAt, "FAILED",
                    totalFetched, totalNew, totalDuplicates, totalFailed, e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected ingestion error: {}", e.getMessage(), e);
            return completeIngestion(ingestionLog, startedAt, "FAILED",
                    totalFetched, totalNew, totalDuplicates, totalFailed, e.getMessage());
        }
    }

    private String determineStatus(int fetched, int newJobs, int duplicates, int failed) {
        if (failed == 0) return "SUCCESS";
        if (newJobs > 0 || duplicates > 0) return "PARTIAL";
        return "FAILED";
    }

    private IngestionResult completeIngestion(IngestionLog ingestionLog, Instant startedAt,
                                               String status, int fetched, int newJobs,
                                               int duplicates, int failed, String error) {
        Instant completedAt = Instant.now();
        long durationMs = Duration.between(startedAt, completedAt).toMillis();

        ingestionLog.setStatus(status);
        ingestionLog.setCompletedAt(completedAt);
        ingestionLog.setDurationMs(durationMs);
        ingestionLog.setTotalFetched(fetched);
        ingestionLog.setTotalNew(newJobs);
        ingestionLog.setTotalDuplicates(duplicates);
        ingestionLog.setTotalFailed(failed);
        ingestionLog.setErrorMessage(error);
        logRepository.save(ingestionLog);

        log.info("Ingestion complete: status={}, fetched={}, new={}, duplicates={}, failed={}, duration={}ms",
                status, fetched, newJobs, duplicates, failed, durationMs);

        return new IngestionResult(status, fetched, newJobs, duplicates, failed, durationMs, error, null);
    }
}
