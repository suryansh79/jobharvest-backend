package com.jobharvest.validation;

import com.jobharvest.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobValidatorTest {

    private JobValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JobValidator();
    }

    @Test
    @DisplayName("Valid job passes validation")
    void testValidJob() {
        Job job = new Job();
        job.setExternalId(101);
        job.setSource("jobicy");
        job.setTitle("Java Developer");
        job.setCompany("TechCorp");
        job.setJobUrl("https://jobicy.com/jobs/101");

        JobValidator.ValidationResult result = validator.validate(job);

        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Job missing required fields fails validation with error list")
    void testInvalidJob() {
        Job job = new Job();
        // missing externalId, title, company, jobUrl, source

        JobValidator.ValidationResult result = validator.validate(job);

        assertFalse(result.valid());
        assertEquals(5, result.errors().size());
        assertTrue(result.errors().contains("Missing external ID"));
        assertTrue(result.errors().contains("Missing or blank title"));
        assertTrue(result.errors().contains("Missing or blank company"));
        assertTrue(result.errors().contains("Missing or blank job URL"));
        assertTrue(result.errors().contains("Missing or blank source"));
    }
}
