# DECISIONS

## 1. Ingestion Strategy
I deliberately selected Jobicy’s public, auth-free Remote Jobs API rather than direct browser/HTML scraping of protected job portals such as LinkedIn, Indeed, Naukri, or Wellfound.

Direct scraping of protected job boards introduces significant operational and compliance risks: anti-bot detection surfaces, browser fingerprinting, behavioral tracking, CAPTCHA barriers, authentication requirements, and Terms of Service (ToS) violations. The assessment explicitly permits a low-risk public job-board API or RSS source.

Consuming a structured public API provided a stable data contract, allowing me to focus engineering effort on core data pipeline reliability—resilient HTTP fetching with bounded retries, payload normalization, field validation, database deduplication, database-backed cooldown tracking, and transactional PostgreSQL persistence—without attempting to bypass anti-bot controls.

## 2. Time-Box Trade-Off
Under the time limit, I chose to make one source pipeline deeply reliable rather than implementing multiple shallow source integrations.

This trade-off enabled me to deliver a complete, production-ready ingestion pipeline with 21 passing automated unit tests, Flyway database migrations, rate-limit cooldown persistence, and a verified live deployment on Render and Neon PostgreSQL. With a full week of development time, I would implement a second authorized public API adapter (such as Arbeitnow) behind the existing `JobSource` abstraction, add dynamic health-based fallback routing, and expand integration monitoring and alerts.

## 3. AI Usage & Personal Verification
AI tools were used throughout development for code scaffolding assistance, debugging, edge-case review, and documentation refinement.

I took full personal ownership of the system and verified every technical component:
- I executed and validated the test suite locally (`./mvnw test` -> 21 passing tests).
- I inspected Render production logs, diagnosed a PostgreSQL parameter type inference bug (`ERROR: function lower(bytea) does not exist`), and resolved it by applying explicit string parameter casting in JPQL (`CAST(:keyword AS string)`).
- I verified all deployed REST endpoints (`GET /`, `GET /health`, `GET /api/jobs`, `GET /api/ingestion/status`).
- I verified the successful production ingestion run (50 fetched, 50 new, 0 duplicates, 0 failed).
