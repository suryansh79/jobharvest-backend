package com.jobharvest.api;

import com.jobharvest.ingestion.IngestionLog;
import com.jobharvest.ingestion.IngestionResult;
import com.jobharvest.ingestion.IngestionService;
import com.jobharvest.repository.IngestionLogRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final IngestionService ingestionService;
    private final IngestionLogRepository logRepository;

    public IngestionController(IngestionService ingestionService, IngestionLogRepository logRepository) {
        this.ingestionService = ingestionService;
        this.logRepository = logRepository;
    }

    @PostMapping("/run")
    public ResponseEntity<IngestionResult> triggerIngestion() {
        IngestionResult result = ingestionService.runIngestion();

        return switch (result.status()) {
            case "RATE_LIMITED" -> {
                HttpHeaders headers = new HttpHeaders();
                if (result.nextAllowedAt() != null) {
                    long retryAfterSeconds = Math.max(1,
                            result.nextAllowedAt().getEpochSecond() - java.time.Instant.now().getEpochSecond());
                    headers.set("Retry-After", String.valueOf(retryAfterSeconds));
                }
                yield ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .headers(headers)
                        .body(result);
            }
            case "ALREADY_RUNNING" -> ResponseEntity.status(HttpStatus.CONFLICT).body(result);
            default -> ResponseEntity.ok(result);
        };
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<IngestionLog> recentLogs = logRepository.findTop10ByOrderByStartedAtDesc();

        if (!recentLogs.isEmpty()) {
            IngestionLog latest = recentLogs.getFirst();
            response.put("latestIngestion", Map.of(
                    "status", latest.getStatus(),
                    "startedAt", latest.getStartedAt().toString(),
                    "completedAt", latest.getCompletedAt() != null ? latest.getCompletedAt().toString() : "in progress",
                    "durationMs", latest.getDurationMs() != null ? latest.getDurationMs() : 0,
                    "totalFetched", latest.getTotalFetched(),
                    "totalNew", latest.getTotalNew(),
                    "totalDuplicates", latest.getTotalDuplicates(),
                    "totalFailed", latest.getTotalFailed(),
                    "errorMessage", latest.getErrorMessage() != null ? latest.getErrorMessage() : ""
            ));
        } else {
            response.put("latestIngestion", "No ingestion runs yet");
        }

        response.put("recentHistory", recentLogs.stream().map(l -> Map.of(
                "status", l.getStatus(),
                "startedAt", l.getStartedAt().toString(),
                "totalFetched", l.getTotalFetched(),
                "totalNew", l.getTotalNew()
        )).toList());

        return response;
    }
}
