package com.jobharvest.repository;

import com.jobharvest.model.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class DeduplicationTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("Database enforces UNIQUE(source, external_id) constraint")
    void testUniqueConstraintEnforced() {
        Job job1 = new Job();
        job1.setExternalId(5001);
        job1.setSource("jobicy");
        job1.setTitle("DevOps Engineer");
        job1.setCompany("CloudCorp");
        job1.setJobUrl("https://jobicy.com/jobs/5001");
        job1.setFetchedAt(Instant.now());

        jobRepository.saveAndFlush(job1);

        assertTrue(jobRepository.existsBySourceAndExternalId("jobicy", 5001));

        // Create duplicate with same source and external_id
        Job duplicateJob = new Job();
        duplicateJob.setExternalId(5001);
        duplicateJob.setSource("jobicy");
        duplicateJob.setTitle("DevOps Engineer (Updated)");
        duplicateJob.setCompany("CloudCorp");
        duplicateJob.setJobUrl("https://jobicy.com/jobs/5001");
        duplicateJob.setFetchedAt(Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> {
            jobRepository.saveAndFlush(duplicateJob);
        });
    }
}
