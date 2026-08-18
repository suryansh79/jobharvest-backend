package com.jobharvest.api;

import com.jobharvest.ingestion.IngestionLog;
import com.jobharvest.ingestion.IngestionResult;
import com.jobharvest.ingestion.IngestionService;
import com.jobharvest.repository.IngestionLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionService ingestionService;

    @MockBean
    private IngestionLogRepository logRepository;

    @Test
    @DisplayName("POST /api/ingestion/run returns 200 OK on SUCCESS status")
    void testTriggerIngestionSuccess() throws Exception {
        IngestionResult successResult = new IngestionResult("SUCCESS", 10, 5, 5, 0, 1500, null, null);
        when(ingestionService.runIngestion()).thenReturn(successResult);

        mockMvc.perform(post("/api/ingestion/run").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalFetched").value(10))
                .andExpect(jsonPath("$.totalNew").value(5));
    }

    @Test
    @DisplayName("POST /api/ingestion/run returns 429 Too Many Requests on RATE_LIMITED status")
    void testTriggerIngestionRateLimited() throws Exception {
        Instant nextAllowed = Instant.now().plusSeconds(1800);
        IngestionResult rateLimitedResult = IngestionResult.rateLimited(Instant.now().minusSeconds(1800), 60);
        when(ingestionService.runIngestion()).thenReturn(rateLimitedResult);

        mockMvc.perform(post("/api/ingestion/run").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("POST /api/ingestion/run returns 409 Conflict on ALREADY_RUNNING status")
    void testTriggerIngestionAlreadyRunning() throws Exception {
        IngestionResult runningResult = IngestionResult.alreadyRunning();
        when(ingestionService.runIngestion()).thenReturn(runningResult);

        mockMvc.perform(post("/api/ingestion/run").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ALREADY_RUNNING"));
    }

    @Test
    @DisplayName("GET /api/ingestion/status returns latest run and history")
    void testGetIngestionStatus() throws Exception {
        IngestionLog logEntry = new IngestionLog();
        logEntry.setSource("jobicy");
        logEntry.setStatus("SUCCESS");
        logEntry.setStartedAt(Instant.now());
        logEntry.setCompletedAt(Instant.now());
        logEntry.setTotalFetched(10);
        logEntry.setTotalNew(3);

        when(logRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of(logEntry));

        mockMvc.perform(get("/api/ingestion/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestIngestion.status").value("SUCCESS"))
                .andExpect(jsonPath("$.recentHistory[0].status").value("SUCCESS"));
    }
}
