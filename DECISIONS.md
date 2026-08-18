# DECISIONS

## 1. Ingestion Strategy
I deliberately selected Jobicy’s public, auth-free Remote Jobs API over direct browser/HTML scraping of protected portals like LinkedIn, Indeed, Naukri, or Wellfound. Direct scraping introduces operational and compliance risks: anti-bot detection, browser fingerprints, behavioral tracking, CAPTCHAs, auth barriers, and ToS violations.

Consuming a structured public API provided a stable data contract, allowing me to focus on core data engineering—resilient fetching, normalization, validation, deduplication, rate control, and PostgreSQL persistence—without attempting to bypass anti-bot or access controls.

## 2. Detection Surface & Hypothetical Strategy
Automated scrapers are detected via browser fingerprints, rigid request timing, header anomalies, session patterns, and IP-level rate limits. In a hypothetical system targeting a protected platform, my strategy would use conservative pacing with randomized jitter, consistent session management, and source isolation to pause queries immediately upon rate-limit signals.

**Submitted Scope**: The submitted implementation consumes Jobicy's public API and does not use browser stealth, IP/proxy rotation, fingerprint spoofing, or CAPTCHA bypass.

## 3. Resilience, Fallback & Boundary
- **Database Cooldown**: Enforces a 60-minute rate limit in PostgreSQL (`ingestion_logs`), surviving restarts and Render sleep cycles.
- **Single-Flight Lock**: Uses an `AtomicBoolean` lock preventing duplicate parallel executions on single-instance runtimes.
- **Bounded Backoff**: Retries 5xx errors/timeouts up to 3 times (2s/4s/8s delays); fails fast on 429/4xx client errors.
- **Validation & Deduplication**: Rejects malformed records; enforces database uniqueness via `CONSTRAINT uq_source_external_id UNIQUE (source, external_id)`.
- **Audit Logging**: Tracks explicit run outcomes (`SUCCESS`, `PARTIAL`, `EMPTY`, `FAILED`, `RATE_LIMITED`).

If blocked, the system backs off and logs an explicit `FAILED` status. Future work includes adding a second authorized public API adapter for fallback.

**Boundary**: I will not bypass authentication, solve CAPTCHAs, spoof fingerprints, or aggressively query a blocking source.

## 4. Time-Box Trade-Off
Under the time limit, I made one source pipeline deeply reliable rather than implementing several shallow integrations. This delivered a complete pipeline with 21 passing tests, Flyway migrations, and a live deployment on Render and Neon PostgreSQL. With more time, I would add a second public API adapter (Arbeitnow), dynamic source health evaluation, and telemetry alerts.

## 5. AI & Ownership
AI tools assisted development, debugging, and documentation. I took personal ownership of the system: I validated the test suite locally (21 passing tests), inspected Render production logs, diagnosed a PostgreSQL parameter type inference bug (`ERROR: function lower(bytea) does not exist`), and fixed it using explicit JPQL string casting (`CAST(:keyword AS string)`). I verified all deployed REST endpoints and confirmed the live ingestion result (50 fetched, 50 new, 0 duplicates, 0 failed).
