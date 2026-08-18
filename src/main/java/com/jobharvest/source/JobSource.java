package com.jobharvest.source;

import java.util.List;

public interface JobSource {

    String getSourceName();

    List<RawJobData> fetchJobs() throws SourceFetchException;
}
