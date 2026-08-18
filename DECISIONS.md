# Architectural Decisions & Trade-Offs (DECISIONS.md)

### 1. Source Selection: Jobicy Public API vs. Hostile Direct Scraping
- **Decision**: Ingest from Jobicy’s public, auth-free Remote Jobs API (`GET /api/v2/remote-jobs`) rather than direct scraping of protected platforms (e.g. LinkedIn or Indeed).
- **Rationale**: Demonstrates complete ingestion engineering (parsing, normalization, validation, deduplication, resilience) without violating terms of service, attempting illegal CAPTCHA bypasses, or incurring IP ban risks.
- **Trade-off**: Data volume is limited to remote job listings provided by public feeds rather than arbitrary scraped sites.

### 2. Stack: Java 21 & Spring Boot 3.4
- **Decision**: Spring Boot monolith using Java 21 LTS baseline and standard Spring Data JPA + Web starter components.
- **Rationale**: Java/Spring Boot provides robust type safety, mature HTTP client capabilities, and standard transactional database access. Java 21 allows clean compilation across local and containerized runtimes.
- **Trade-off**: Higher JVM memory footprint (~250-350 MB RAM) compared to lightweight runtimes (e.g. Go), requiring careful tuning for 512 MB free container tiers.

### 3. Infrastructure: Render Free Web Service + Neon Free PostgreSQL
- **Decision**: Host web application on Render Free Web Service (Docker) and database on Neon Free PostgreSQL.
- **Rationale**: Render's PostgreSQL free tier auto-deletes databases after 30 days. Neon provides a non-expiring PostgreSQL instance (0.5 GB storage, 100 CU-hours/month compute). Combined, they achieve a verified ₹0/$0 deployment without billing risk.
- **Trade-off**: Both platforms scale to zero when idle, introducing a 60–90 second cold-start delay on initial access.

### 4. Production Scheduling: GitHub Actions vs. Spring `@Scheduled`
- **Decision**: External hourly trigger via GitHub Actions (`.github/workflows/ingestion.yml`) calling `POST /api/ingestion/run`.
- **Rationale**: Render Free Web Service sleeps after 15 minutes of inactivity. An internal Spring `@Scheduled` runner cannot execute while the JVM container is down. GitHub Actions wakes the web service and triggers ingestion reliably.
- **Trade-off**: External dependency on GitHub Actions runner execution timing.

### 5. Single Source vs. Multi-Source Complexity
- **Decision**: Implement one robust source adapter (`JobicySource`) behind a clean `JobSource` interface.
- **Rationale**: One fully verified source with comprehensive test coverage demonstrates source abstraction and engineering rigor without bloat.
- **Trade-off**: Additional source adapters (e.g. Arbeitnow) exist as documented patterns rather than active production code.

### 6. Fallback Strategy: Operator-Driven vs. Automatic Failover
- **Decision**: Operator-driven fallback (implementing a new `JobSource` adapter and redeploying via configuration).
- **Rationale**: Claiming "automatic failover" without multi-active source polling is misleading. Serving previously persisted PostgreSQL data during upstream outages provides immediate resilience; source replacement is handled via deliberate operator action.
- **Trade-off**: Source migration requires an operator code/config push rather than automated runtime switching.

### 7. Explicit Non-Goals & Omitted Technologies
- **Intentionally Omitted**: Kafka, Redis, RabbitMQ, Elasticsearch, Kubernetes, React Frontend.
- **Rationale**: Adding distributed message queues, in-memory caches, or single-page applications to a low-volume hourly ingestion service represents unnecessary complexity ("resume-driven development") that cannot be justified in a technical interview.

### 8. AI Usage & Verification Disclosure
- **AI Assistance**: AI tools were utilized for architectural analysis, boilerplate scaffolding, and test suite generation.
- **Manual Verification**: All source code, schema migrations (`V1__create_schema.sql`), rate-limiting algorithms, and unit test suites were compiled and executed locally (`./mvnw test` -> 21 passing tests) and verified against live API responses.
