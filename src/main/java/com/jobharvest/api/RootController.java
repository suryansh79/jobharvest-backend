package com.jobharvest.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "JobHarvest");
        info.put("description", "Production-ready job listing ingestion and search API with validation, deduplication, resilient ingestion, PostgreSQL persistence, filtering, and pagination.");
        info.put("purpose", "Fetches job listings from the Jobicy public API, normalizes and validates the data, prevents duplicates, persists jobs in PostgreSQL, and exposes searchable paginated REST APIs.");
        info.put("source", "Jobicy Remote Jobs API (public, no auth)");
        info.put("timestamp", Instant.now().toString());
        info.put("endpoints", Map.of(
                "GET /", "This page — service information",
                "GET /health", "Health check (database connectivity)",
                "GET /api/jobs", "List jobs (query params: keyword, location, page, size)",
                "GET /api/jobs/{id}", "Get a single job by internal ID",
                "GET /api/ingestion/status", "Latest ingestion result and recent history",
                "POST /api/ingestion/run", "Trigger manual ingestion (cooldown protected)"
        ));
        return info;
    }
}
