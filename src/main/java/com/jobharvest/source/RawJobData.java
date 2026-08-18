package com.jobharvest.source;

import java.util.List;

public record RawJobData(
        Integer id,
        String url,
        String jobTitle,
        String companyName,
        List<String> jobIndustry,
        List<String> jobType,
        String jobGeo,
        String jobLevel,
        String jobExcerpt,
        String jobDescription,
        String pubDate,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        String salaryPeriod
) {}
