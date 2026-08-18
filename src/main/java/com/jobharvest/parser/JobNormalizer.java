package com.jobharvest.parser;

import com.jobharvest.model.Job;
import com.jobharvest.source.RawJobData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class JobNormalizer {

    private static final Logger log = LoggerFactory.getLogger(JobNormalizer.class);

    public Job normalize(RawJobData raw, String sourceName) {
        Job job = new Job();
        job.setExternalId(raw.id());
        job.setSource(sourceName);
        job.setTitle(trimOrNull(raw.jobTitle()));
        job.setCompany(trimOrNull(raw.companyName()));
        job.setLocation(trimOrNull(raw.jobGeo()));
        job.setDescription(raw.jobDescription());
        job.setExcerpt(truncate(raw.jobExcerpt(), 2000));
        job.setJobUrl(trimOrNull(raw.url()));
        job.setJobType(joinList(raw.jobType()));
        job.setJobLevel(trimOrNull(raw.jobLevel()));
        job.setIndustry(joinList(raw.jobIndustry()));
        job.setSalaryMin(raw.salaryMin());
        job.setSalaryMax(raw.salaryMax());
        job.setSalaryCurrency(trimOrNull(raw.salaryCurrency()));
        job.setPublishedAt(parseDate(raw.pubDate()));
        job.setFetchedAt(Instant.now());
        return job;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String joinList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return null;
        String joined = String.join(", ", values);
        return truncate(joined, 500);
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            try {
                return java.time.OffsetDateTime.parse(dateStr).toInstant();
            } catch (DateTimeParseException e2) {
                log.warn("Could not parse date '{}': {}", dateStr, e2.getMessage());
                return null;
            }
        }
    }
}
