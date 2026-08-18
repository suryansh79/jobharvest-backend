package com.jobharvest.api;

import com.jobharvest.model.Job;
import com.jobharvest.repository.JobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobRepository jobRepository;

    @Test
    @DisplayName("GET /api/jobs returns paginated job list")
    void testListJobs() throws Exception {
        Job job = new Job();
        job.setId(1L);
        job.setTitle("Senior Java Engineer");
        job.setCompany("Adycon");

        when(jobRepository.findByFilters(eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(job)));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Senior Java Engineer"))
                .andExpect(jsonPath("$.content[0].company").value("Adycon"));
    }

    @Test
    @DisplayName("GET /api/jobs/{id} returns 200 when found")
    void testGetJobFound() throws Exception {
        Job job = new Job();
        job.setId(1L);
        job.setTitle("Architect");
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Architect"));
    }

    @Test
    @DisplayName("GET /api/jobs/{id} returns 404 when not found")
    void testGetJobNotFound() throws Exception {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/jobs/99"))
                .andExpect(status().isNotFound());
    }
}
