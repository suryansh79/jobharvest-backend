# JobHarvest — Job Listing Ingestion Backend

> **Acdyon Technologies — Frontend Challenge, Part 1: Getting Data Out of a Platform That Doesn't Want You To**

JobHarvest is a production-minded Spring Boot backend service that automatically ingests, normalizes, validates, deduplicates, and persists job listings from public APIs, exposing them through a clean REST API.

---

## 1. Zero-Cost Architecture (₹0 / $0 Total)

```
                    GitHub Actions Workflow
                    (.github/workflows/ingestion.yml)
                               │
                       ~hourly schedule (cron: 17 * * * *)
                               │
                               ▼
                    POST /api/ingestion/run
                               │
                               ▼
                     Render Free Web Service
                   (Docker / JDK 21 Runtime)
                   (wakes from sleep on hit)
                               │
                               ▼
                        Spring Boot 3.4
                               │
            ┌──────────────────┼──────────────────┐
            ▼                  ▼                  ▼
     Database Cooldown    Atomic Lock      Jobicy Source Adapter
      (ingestion_logs)   (Single-Flight)      (HTTP Client + Retry)
            │                  │                  │
            └──────────────────┼──────────────────┘
                               │
                          Jobicy API
                     (public, auth-free)
                               │
                               ▼
                    Parse → Normalize → Validate
                               │
                               ▼
                          Deduplicate
                     (UNIQUE constraint)
                               │
                               ▼
                      Neon Free PostgreSQL
                   (Flyway Schema Migrations)
                               │
                               ▼
                            REST API
                               ▲
                               │
                           Evaluator
```

### Architectural Decisions & Trade-Offs

| Decision | Selection | Alternative Rejected | Rationale |
|----------|-----------|----------------------|-----------|
| **Production Scheduler** | GitHub Actions | Internal `@Scheduled` | Render Free Web Service spins down after 15 minutes of inactivity. An internal JVM scheduler cannot execute while the service is asleep. GitHub Actions acts as an external trigger that wakes the service. |
| **Web Hosting** | Render Free Web Service | Railway / Fly.io | Render offers a persistent free tier (750 instance hours/month, no credit card required). Railway and Fly.io require credit cards or paid plans after trials. |
| **Database** | Neon Free PostgreSQL | Render Free PostgreSQL | Render's free PostgreSQL databases expire and are permanently deleted after 30 days. Neon provides a non-expiring free PostgreSQL tier (0.5 GB storage, 100 CU-hours/month compute). |
| **Source Cooldown** | Database-Backed (`ingestion_logs`) | In-Memory Timestamp | In-memory timestamps reset when Render sleeps or restarts. Persisting the last attempt timestamp in PostgreSQL ensures the 60-minute upstream rate limit is enforced across restarts. |
| **Concurrency** | `AtomicBoolean` Single-Flight Lock | Redis / Distributed Lock | Render runs as a single-instance free deployment. A single-flight `AtomicBoolean` lock prevents duplicate concurrent fetches without adding Redis or distributed locking infrastructure. |
| **Schema Management** | Flyway Migrations | `ddl-auto=update` | Flyway provides deterministic, version-controlled schema initialization (`V1__create_schema.sql`). `ddl-auto=update` is non-deterministic and unsafe for production. |
| **Fallback Strategy** | Operator-Driven Fallback | Automatic Multi-Source Failover | Switching to an alternative source (e.g. Arbeitnow) requires code/config change and redeployment. Claiming "automatic failover" without implementing multi-active ingestion would be dishonest. |

---

## 2. Cost & Free-Tier Limits Audit

Every third-party dependency is 100% free with **zero billing risk** (no credit card required):

| Service | Free-Tier Allowance | Expiration Policy | Billing Risk |
|---------|---------------------|-------------------|--------------|
| **Render Web Service** | 750 free instance hours/month | Permanent free tier | **None**: No payment method on file; service suspends if quota exceeded. |
| **Neon PostgreSQL** | 0.5 GB storage, 100 CU-hours/month | Permanent free tier | **None**: Project suspends on quota limit; no billing. |
| **GitHub Actions** | Standard GitHub-hosted runners free for public repositories | Permanent free tier | **None**: Unlimited standard runner minutes for public repos. |
| **Jobicy API** | Public Remote Jobs API (no API key required) | Fair-use policy | **None**: Free public feed. |
| **TOTAL** | | | **₹0 / $0** |

---

## 3. Cold-Start Behavior

The deployed demo on Render Free Web Service is subject to free-tier sleep policies:
- **Web Instance Sleep**: Render spins down the web instance after 15 minutes of inactivity.
- **First Request Delay**: The first request (by an evaluator or GitHub Actions) triggers a "cold start" taking ~60-90 seconds to launch the container and initialize the JVM.
- **Database Scale-to-Zero**: Neon suspends compute after 5 minutes of inactivity; the first database query wakes it within ~1-2 seconds.

*We accept cold starts as an honest ₹0 trade-off and do not use artificial ping services (like UptimeRobot) to defeat sleep behavior.*

---

## 4. API Endpoints

### Endpoint Summary

| Method | Path | Description | Expected Status Codes |
|--------|------|-------------|-----------------------|
| `GET` | `/` | Service overview, endpoint sitemap, and status | `200 OK` |
| `GET` | `/health` | Spring Actuator health check (DB connectivity) | `200 OK` / `503 Service Unavailable` |
| `GET` | `/api/jobs` | Paginated job list with optional search filters | `200 OK` |
| `GET` | `/api/jobs/{id}` | Single job detail by internal database ID | `200 OK` / `404 Not Found` |
| `GET` | `/api/ingestion/status` | Latest ingestion run details and recent history | `200 OK` |
| `POST` | `/api/ingestion/run` | Manually trigger job ingestion | `200 OK` (SUCCESS/PARTIAL/EMPTY/FAILED), `429 Too Many Requests` (cooldown), `409 Conflict` (running) |

### Query Parameters for `GET /api/jobs`
- `keyword`: Filters title and company name (case-insensitive substring match)
- `location`: Filters location string (case-insensitive substring match)
- `page`: Page number (0-indexed, default `0`)
- `size`: Page size (default `20`, maximum `100`)

---

## 5. Ingestion State & Resilience Policy

### Ingestion States (`ingestion_logs.status`)
- `SUCCESS`: All fetched job listings were successfully parsed, validated, and persisted.
- `PARTIAL`: Ingestion completed, but some individual records failed validation or parsing.
- `EMPTY`: Source returned a valid HTTP 200 response with 0 jobs (`"jobs": []`).
- `FAILED`: Upstream HTTP error, timeout, malformed JSON body, or unhandled exception.
- `RATE_LIMITED`: Attempt rejected because the 60-minute source cooldown was active.

### Upstream Rate Protection & Concurrency Ordering
Every ingestion trigger (`POST /api/ingestion/run`) executes the following race-safe sequence:
1. **DB Cooldown Check**: Queries `ingestion_logs` for the latest `started_at` timestamp. If `< 60 minutes` ago, returns `429 Too Many Requests` immediately (0 HTTP requests sent to Jobicy).
2. **Concurrency Acquisition**: Acquires non-blocking `AtomicBoolean` lock. If lock is already held, returns `409 Conflict` (`ALREADY_RUNNING`).
3. **Double-Check Cooldown**: Re-checks database cooldown under lock to prevent race conditions.
4. **Record Attempt**: Persists `IngestionLog(status="RUNNING", startedAt=now())` to PostgreSQL *before* initiating HTTP fetch.
5. **Fetch & Process**: Calls Jobicy API with bounded retries (max 3 attempts, exponential backoff: 2s, 4s, 8s). Only transient errors (5xx, timeout, connection failure) trigger retries. 429 or 4xx errors fail fast without retrying.

---

## 6. Local Development & Testing

### Prerequisites
- JDK 21 (or JDK 25)
- Maven Wrapper (`./mvnw`)

### Running Unit Tests
```bash
./mvnw test
```
*All unit tests use mocked HTTP responses and an in-memory H2 database. No live external API calls are made during testing.*

### Running the Application Locally
```bash
./mvnw spring-boot:run
```
The application will start on `http://localhost:8080` using an in-memory H2 database by default.

### Triggering Ingestion Locally
```bash
curl -X POST http://localhost:8080/api/ingestion/run
```

---

## 7. Deployment Instructions (Render + Neon)

1. **Neon PostgreSQL Setup**:
   - Create a free project on [Neon.tech](https://neon.tech).
   - Copy the PostgreSQL connection string.

2. **Render Setup**:
   - Connect GitHub repository to [Render.com](https://render.com).
   - Create a new **Web Service** using the root `Dockerfile`.
   - Configure Environment Variables:
     - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://<neon-host>/neondb?sslmode=require`
     - `SPRING_DATASOURCE_USERNAME` = `<neon-username>`
     - `SPRING_DATASOURCE_PASSWORD` = `<neon-password>`

3. **GitHub Actions Setup**:
   - Go to GitHub Repository Settings -> Secrets and variables -> Actions.
   - Add Secret `RENDER_URL` = `https://<your-render-app>.onrender.com`.
