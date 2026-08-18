package com.jobharvest.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobharvest.config.IngestionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JobicySourceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private JobicySource jobicySource;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        IngestionProperties props = new IngestionProperties();
        props.setSourceUrl("https://jobicy.com/api/v2/remote-jobs");
        props.setMaxRetries(2);
        props.setBackoffBaseMs(10);
        ObjectMapper objectMapper = new ObjectMapper();

        jobicySource = new JobicySource(restTemplate, props, objectMapper);
    }

    @Test
    @DisplayName("Should successfully fetch and parse valid Jobicy JSON")
    void testFetchSuccess() {
        String json = """
            {
              "apiVersion": "2.2.15",
              "jobCount": 1,
              "jobs": [
                {
                  "id": 1001,
                  "url": "https://jobicy.com/jobs/1001",
                  "jobTitle": "Backend Engineer",
                  "companyName": "Acme Inc"
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://jobicy.com/api/v2/remote-jobs"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<RawJobData> jobs = jobicySource.fetchJobs();

        assertEquals(1, jobs.size());
        assertEquals(1001, jobs.get(0).id());
        assertEquals("Backend Engineer", jobs.get(0).jobTitle());
        assertEquals("Acme Inc", jobs.get(0).companyName());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw non-retryable SourceFetchException on 429 rate limit")
    void testFetchRateLimited() {
        mockServer.expect(requestTo("https://jobicy.com/api/v2/remote-jobs"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        SourceFetchException ex = assertThrows(SourceFetchException.class, () -> jobicySource.fetchJobs());

        assertEquals(429, ex.getHttpStatus());
        assertFalse(ex.isRetryable());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should retry on 500 server error up to maxRetries")
    void testFetchServerErrorRetryExhaustion() {
        mockServer.expect(requestTo("https://jobicy.com/api/v2/remote-jobs"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://jobicy.com/api/v2/remote-jobs"))
                .andRespond(withServerError());

        SourceFetchException ex = assertThrows(SourceFetchException.class, () -> jobicySource.fetchJobs());

        assertEquals(500, ex.getHttpStatus());
        assertTrue(ex.isRetryable());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw non-retryable exception on malformed JSON response")
    void testFetchMalformedJson() {
        mockServer.expect(requestTo("https://jobicy.com/api/v2/remote-jobs"))
                .andRespond(withSuccess("{ invalid json ...", MediaType.APPLICATION_JSON));

        SourceFetchException ex = assertThrows(SourceFetchException.class, () -> jobicySource.fetchJobs());

        assertFalse(ex.isRetryable());
        assertTrue(ex.getMessage().contains("Failed to parse Jobicy response"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return empty list when source returns 0 jobs")
    void testFetchEmptyJobsArray() {
        String json = """
            {
              "apiVersion": "2.2.15",
              "jobCount": 0,
              "jobs": []
            }
            """;

        mockServer.expect(requestTo("https://jobicy.com/api/v2/remote-jobs"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<RawJobData> jobs = jobicySource.fetchJobs();

        assertTrue(jobs.isEmpty());
        mockServer.verify();
    }
}
