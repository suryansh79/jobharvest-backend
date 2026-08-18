package com.jobharvest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, IngestionProperties props) {
        return builder
                .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .build();
    }

    @Bean
    @ConfigurationProperties(prefix = "app.ingestion")
    public IngestionProperties ingestionProperties() {
        return new IngestionProperties();
    }
}
