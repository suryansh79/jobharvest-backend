# DECISIONS

## 1. Ingestion Strategy
I chose to ingest job listings from Jobicy's public, auth-free Remote Jobs API rather than performing direct browser/HTML scraping on protected platforms like LinkedIn, Indeed, Naukri, or Wellfound.

Direct scraping of protected job portals introduces browser fingerprint detection, behavioral analysis, CAPTCHA challenges, authentication requirements, and high Terms of Service (ToS) compliance risks. For this assignment, browser-level anti-bot detection is largely outside our attack surface because we consume a structured public API endpoint.

Consuming an authorized public API allowed me to focus the implementation on core data engineering concerns—building a resilient HTTP fetching pipeline, payload normalization, strict field validation, database deduplication, rate control, and transactional PostgreSQL persistence—without attempting to bypass security mechanisms or risk IP bans.

## 2. Resilience, Fallback & Boundary
The ingestion pipeline builds resilience across multiple layers:
- **Database-Backed Cooldown**: Enforces a 60-minute rate limit stored in PostgreSQL (`ingestion_logs`), surviving application restarts, container redeployments, and Render free-tier sleep cycles.
- **Single-Flight Concurrency Lock**: Uses an `AtomicBoolean` lock to prevent duplicate parallel executions on single-instance runtimes.
- **Bounded Exponential Backoff**: Retries transient 5xx server errors and connection timeouts up to 3 times (with 2s, 4s, 8s delays), while failing fast on 429 and 4xx client errors.
- **Validation & Deduplication**: Rejects malformed records and enforces database-level uniqueness via `CONSTRAINT uq_source_external_id UNIQUE (source, external_id)`.
- **Ingestion Audit Trail**: Records explicit run outcomes (`SUCCESS`, `PARTIAL`, `EMPTY`, `FAILED`, `RATE_LIMITED`) with full duration and count metrics.

**Fallback & Boundary**: If the primary source becomes unavailable or returns persistent error responses, the system backs off and records an explicit `FAILED` audit status rather than attempting aggressive evasion or stealth automation. In a production environment with more development time, I would place additional permitted public API or RSS adapters behind the `JobSource` interface to enable authorized multi-source fallback.

**Technical & ToS Boundary**: I would not bypass authentication, solve CAPTCHAs, spoof browser fingerprints, or aggressively query an endpoint that is actively blocking requests.

## 3. Time-Box Trade-Off
Under the time limit, I chose to make one source pipeline deeply reliable rather than building multiple partially complete source adapters.

This approach allowed me to build and verify a complete end-to-end ingestion lifecycle with 21 automated unit and integration tests, database-backed cooldown persistence, single-flight locking, Flyway migrations, and a verified live production deployment on Render and Neon PostgreSQL.

With a full week of development time, I would implement:
1. A second authorized public API or RSS source adapter (e.g., Arbeitnow) behind the existing `JobSource` interface.
2. Dynamic source health evaluation and automated fallback selection.
3. Expanded integration testing and end-to-end telemetry monitoring.

## 4. AI & Ownership
AI tools were used throughout development for architectural brainstorming, boilerplate scaffolding, unit test generation, and documentation formatting.

I took personal ownership of the implementation and verified every component:
- I executed and validated the application and automated test suite locally (`./mvnw test` -> 21 passing tests).
- I analyzed production error logs and diagnosed the PostgreSQL parameter type inference bug (`ERROR: function lower(bytea) does not exist`), resolving it by applying explicit String parameter casting in JPQL (`CAST(:keyword AS string)`).
- I verified the live production deployment on Render and Neon PostgreSQL, testing all REST endpoints (`GET /`, `GET /health`, `GET /api/jobs`, `GET /api/ingestion/status`).
- I confirmed the initial production ingestion run succeeded (50 fetched, 50 new, 0 duplicates, 0 failed).
- I reviewed, tested, and approved all code submitted in this repository.
