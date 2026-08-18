package com.jobharvest.ingestion;

import java.time.Instant;

public record IngestionResult(
        String status,
        int totalFetched,
        int totalNew,
        int totalDuplicates,
        int totalFailed,
        long durationMs,
        String errorMessage,
        Instant nextAllowedAt
) {
    public static IngestionResult rateLimited(Instant lastAttempt, int cooldownMinutes) {
        Instant nextAllowed = lastAttempt.plusSeconds(cooldownMinutes * 60L);
        return new IngestionResult("RATE_LIMITED", 0, 0, 0, 0, 0,
                "Source cooldown active. Last fetch attempted at " + lastAttempt,
                nextAllowed);
    }

    public static IngestionResult alreadyRunning() {
        return new IngestionResult("ALREADY_RUNNING", 0, 0, 0, 0, 0,
                "Another ingestion is currently in progress", null);
    }
}
