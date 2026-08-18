package com.jobharvest.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobharvest.config.IngestionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
public class JobicySource implements JobSource {

    private static final Logger log = LoggerFactory.getLogger(JobicySource.class);
    private static final String SOURCE_NAME = "jobicy";

    private final RestTemplate restTemplate;
    private final IngestionProperties properties;
    private final ObjectMapper objectMapper;

    public JobicySource(RestTemplate restTemplate, IngestionProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<RawJobData> fetchJobs() throws SourceFetchException {
        String url = properties.getSourceUrl();
        int maxRetries = properties.getMaxRetries();
        long backoffBaseMs = properties.getBackoffBaseMs();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Fetching jobs from Jobicy (attempt {}/{}): {}", attempt, maxRetries, url);
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                return parseResponse(response.getBody());

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 429) {
                    log.warn("Jobicy returned 429 (rate limited). Will not retry.");
                    throw new SourceFetchException("Rate limited by Jobicy (429)", status, false);
                }
                log.error("Jobicy returned client error {}: {}", status, e.getMessage());
                throw new SourceFetchException("Client error from Jobicy: " + status, status, false);

            } catch (HttpServerErrorException e) {
                int status = e.getStatusCode().value();
                log.warn("Jobicy returned server error {} (attempt {}/{})", status, attempt, maxRetries);
                if (attempt == maxRetries) {
                    throw new SourceFetchException("Server error from Jobicy after " + maxRetries + " attempts: " + status, status, true);
                }
                sleepWithBackoff(attempt, backoffBaseMs);

            } catch (ResourceAccessException e) {
                log.warn("Connection/timeout error fetching from Jobicy (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    throw new SourceFetchException("Connection error after " + maxRetries + " attempts: " + e.getMessage(), e, true);
                }
                sleepWithBackoff(attempt, backoffBaseMs);
            }
        }
        throw new SourceFetchException("Exhausted all retry attempts", -1, false);
    }

    private List<RawJobData> parseResponse(String body) throws SourceFetchException {
        if (body == null || body.isBlank()) {
            throw new SourceFetchException("Empty response body from Jobicy", 200, false);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode jobsNode = root.get("jobs");

            if (jobsNode == null || !jobsNode.isArray()) {
                throw new SourceFetchException("Response missing 'jobs' array — possible API format change", 200, false);
            }

            List<RawJobData> jobs = new ArrayList<>();
            for (JsonNode node : jobsNode) {
                try {
                    jobs.add(parseJobNode(node));
                } catch (Exception e) {
                    log.warn("Skipping malformed job entry: {}", e.getMessage());
                }
            }
            return jobs;

        } catch (SourceFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceFetchException("Failed to parse Jobicy response: " + e.getMessage(), e, false);
        }
    }

    private RawJobData parseJobNode(JsonNode node) {
        return new RawJobData(
                getInt(node, "id"),
                getText(node, "url"),
                getText(node, "jobTitle"),
                getText(node, "companyName"),
                getTextList(node, "jobIndustry"),
                getTextList(node, "jobType"),
                getText(node, "jobGeo"),
                getText(node, "jobLevel"),
                getText(node, "jobExcerpt"),
                getText(node, "jobDescription"),
                getText(node, "pubDate"),
                getInt(node, "salaryMin"),
                getInt(node, "salaryMax"),
                getText(node, "salaryCurrency"),
                getText(node, "salaryPeriod")
        );
    }

    private String getText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }

    private Integer getInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull() && value.isNumber()) ? value.asInt() : null;
    }

    private List<String> getTextList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isNull()) result.add(item.asText());
        }
        return result;
    }

    private void sleepWithBackoff(int attempt, long backoffBaseMs) {
        long delay = backoffBaseMs * (1L << (attempt - 1));
        log.info("Backing off for {}ms before retry", delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
