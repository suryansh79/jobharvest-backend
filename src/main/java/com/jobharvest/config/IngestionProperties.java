package com.jobharvest.config;

public class IngestionProperties {

    private String sourceUrl = "https://jobicy.com/api/v2/remote-jobs?count=50";
    private int cooldownMinutes = 60;
    private int maxRetries = 3;
    private long backoffBaseMs = 2000;
    private int timeoutSeconds = 30;

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getBackoffBaseMs() { return backoffBaseMs; }
    public void setBackoffBaseMs(long backoffBaseMs) { this.backoffBaseMs = backoffBaseMs; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
