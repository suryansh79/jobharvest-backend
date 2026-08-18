package com.jobharvest.source;

public class SourceFetchException extends RuntimeException {

    private final int httpStatus;
    private final boolean retryable;

    public SourceFetchException(String message, int httpStatus, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public SourceFetchException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.httpStatus = -1;
        this.retryable = retryable;
    }

    public int getHttpStatus() { return httpStatus; }
    public boolean isRetryable() { return retryable; }
}
