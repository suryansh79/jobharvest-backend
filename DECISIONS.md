# DECISIONS

## 1. Ingestion Strategy
I deliberately selected Jobicy’s public, auth-free Remote Jobs API over direct browser/HTML scraping of protected portals like LinkedIn, Indeed, Naukri, or Wellfound. Direct scraping of protected job boards introduces significant operational and compliance risks: large anti-bot detection surfaces, headless browser fingerprints, behavioral tracking, CAPTCHA challenges, authentication barriers, and ToS violations. The assessment permits a low-risk public job-board API. Using a structured API provided a stable data contract, allowing me to focus on core data pipeline engineering—resilient fetching, payload normalization, field validation, deduplication, rate control, and PostgreSQL persistence—without attempting to bypass anti-bot or access controls.

## 2. Detection Surface & Hypothetical Strategy
In hostile scraping environments, automated clients are detected via headless browser fingerprints, rigid request timing, abnormal frequencies, header anomalies, session patterns, and IP-level rate limits. In a hypothetical production system targeting a protected platform, my strategy would use conservative request pacing with randomized jitter, consistent session/header management, and source-adapter isolation to pause queries immediately upon receiving rate-limit or blocking signals.

**Submitted Scope**: The submitted implementation consumes Jobicy's public API and does not use browser stealth, IP/proxy rotation, fingerprint spoofing, or CAPTCHA bypass.

## 3. Resilience, Fallback & Boundary
The pipeline implements resilience across multiple layers:
- **Database-Backed Cooldown**: Enforces a 60-minute rate limit in PostgreSQL (`ingestion_logs`), surviving application restarts and Render sleep cycles.
- **Single-Flight Lock**: Uses an `AtomicBoolean` lock to prevent duplicate parallel executions on single-instance runtimes.
- **Bounded Backoff**: Retries 5xx errors and timeouts up to 3 times (2s/4s/8s delays); fails fast on 429/4xx errors.
- **Validation & Deduplication**: Rejects malformed records and enforces database uniqueness via `CONSTRAINT uq_source_external_id UNIQUE (source, external_id)`.
- **Audit Logging**: Tracks explicit outcomes (`SUCCESS`, `PARTIAL`, `EMPTY`, `FAILED`, `RATE_LIMITED`).

If a source blocks requests, the system backs off and logs an explicit `FAILED` status rather than attempting aggressive evasion. API schema drift is handled via DTO parsing, validation, and error visibility.

**Boundary**: I will not bypass authentication, solve CAPTCHAs, spoof fingerprints to defeat explicit access controls, or aggressively query a blocking source.

## 4. Time-Box Trade-Off
Under the time limit, I chose to make one source pipeline deeply reliable instead of implementing several shallow source integrations. This trade-off enabled me to deliver a fully working end-to-end pipeline with 21 passing automated tests, database-backed cooldown persistence, Flyway schema migrations, and a live deployment on Render and Neon PostgreSQL.

With a full week, I would:
1. Add a second authorized public API adapter (e.g., Arbeitnow) behind the `JobSource` abstraction.
2. Build dynamic source health evaluation and automated fallback routing.
3. Expand end-to-end integration tests and monitoring alerts.

## 5. AI & Ownership
AI tools were used for development assistance, boilerplate scaffolding, debugging, edge-case review, and documentation refinement.

I took personal ownership of the system and verified every component:
- I executed and validated the automated test suite locally (`./mvnw test` -> 21 passing tests).
- I inspected Render production logs, diagnosed a PostgreSQL parameter type inference bug (`ERROR: function lower(bytea) does not exist`), and fixed it by adding explicit string parameter casting in JPQL (`CAST(:keyword AS string)`).
- I verified all deployed REST endpoints (`GET /`, `GET /health`, `GET /api/jobs`, `GET /api/ingestion/status`).
- I verified the production ingestion result (50 fetched, 50 new, 0 duplicates, 0 failed).
