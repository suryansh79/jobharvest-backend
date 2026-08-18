package com.jobharvest.parser;

import com.jobharvest.model.Job;
import com.jobharvest.source.RawJobData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobNormalizerTest {

    private JobNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new JobNormalizer();
    }

    @Test
    @DisplayName("Should correctly normalize valid RawJobData into a Job entity")
    void testNormalizeValidJob() {
        RawJobData raw = new RawJobData(
                12345,
                "https://jobicy.com/jobs/12345-software-engineer",
                "  Senior Software Engineer  ",
                " Acme Corp ",
                List.of("Engineering", "Software"),
                List.of("Full-Time"),
                "Remote, US",
                "Senior",
                "Short excerpt",
                "<p>Full description HTML</p>",
                "2026-08-18T10:00:00Z",
                100000,
                150000,
                "USD",
                "yearly"
        );

        Job job = normalizer.normalize(raw, "jobicy");

        assertEquals(12345, job.getExternalId());
        assertEquals("jobicy", job.getSource());
        assertEquals("Senior Software Engineer", job.getTitle());
        assertEquals("Acme Corp", job.getCompany());
        assertEquals("Remote, US", job.getLocation());
        assertEquals("<p>Full description HTML</p>", job.getDescription());
        assertEquals("Short excerpt", job.getExcerpt());
        assertEquals("https://jobicy.com/jobs/12345-software-engineer", job.getJobUrl());
        assertEquals("Full-Time", job.getJobType());
        assertEquals("Senior", job.getJobLevel());
        assertEquals("Engineering, Software", job.getIndustry());
        assertEquals(100000, job.getSalaryMin());
        assertEquals(150000, job.getSalaryMax());
        assertEquals("USD", job.getSalaryCurrency());
        assertNotNull(job.getPublishedAt());
        assertNotNull(job.getFetchedAt());
    }

    @Test
    @DisplayName("Should handle missing and blank fields gracefully")
    void testNormalizeMissingFields() {
        RawJobData raw = new RawJobData(
                999,
                "  ",
                null,
                "",
                null,
                List.of(),
                null,
                null,
                null,
                null,
                "invalid-date",
                null,
                null,
                null,
                null
        );

        Job job = normalizer.normalize(raw, "jobicy");

        assertEquals(999, job.getExternalId());
        assertNull(job.getTitle());
        assertNull(job.getCompany());
        assertNull(job.getJobUrl());
        assertNull(job.getIndustry());
        assertNull(job.getJobType());
        assertNull(job.getPublishedAt());
    }
}
