CREATE TABLE IF NOT EXISTS jobs (
    id              BIGSERIAL PRIMARY KEY,
    external_id     INTEGER NOT NULL,
    source          VARCHAR(50) NOT NULL,
    title           VARCHAR(500) NOT NULL,
    company         VARCHAR(300) NOT NULL,
    location        VARCHAR(500),
    description     TEXT,
    excerpt         VARCHAR(2000),
    job_url         VARCHAR(2000) NOT NULL,
    job_type        VARCHAR(100),
    job_level       VARCHAR(100),
    industry        VARCHAR(500),
    salary_min      INTEGER,
    salary_max      INTEGER,
    salary_currency VARCHAR(10),
    published_at    TIMESTAMP,
    fetched_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_source_external_id UNIQUE (source, external_id)
);

CREATE TABLE IF NOT EXISTS ingestion_logs (
    id               BIGSERIAL PRIMARY KEY,
    source           VARCHAR(50) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    started_at       TIMESTAMP NOT NULL,
    completed_at     TIMESTAMP,
    duration_ms      BIGINT,
    total_fetched    INTEGER DEFAULT 0,
    total_new        INTEGER DEFAULT 0,
    total_duplicates INTEGER DEFAULT 0,
    total_failed     INTEGER DEFAULT 0,
    error_message    TEXT
);

CREATE INDEX IF NOT EXISTS idx_jobs_source ON jobs(source);
CREATE INDEX IF NOT EXISTS idx_jobs_title ON jobs(title);
CREATE INDEX IF NOT EXISTS idx_ingestion_logs_started_at ON ingestion_logs(started_at DESC);
