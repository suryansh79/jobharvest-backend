package com.jobharvest.validation;

import com.jobharvest.model.Job;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JobValidator {

    public ValidationResult validate(Job job) {
        List<String> errors = new ArrayList<>();

        if (job.getExternalId() == null) {
            errors.add("Missing external ID");
        }
        if (isBlank(job.getTitle())) {
            errors.add("Missing or blank title");
        }
        if (isBlank(job.getCompany())) {
            errors.add("Missing or blank company");
        }
        if (isBlank(job.getJobUrl())) {
            errors.add("Missing or blank job URL");
        }
        if (isBlank(job.getSource())) {
            errors.add("Missing or blank source");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ValidationResult(boolean valid, List<String> errors) {}
}
