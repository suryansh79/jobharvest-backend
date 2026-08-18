# JobHarvest

JobHarvest is a production-minded Spring Boot backend service that automatically ingests, normalizes, validates, deduplicates, and persists remote job listings from public APIs into a PostgreSQL database, exposing them via a clean REST API surface.

---

## Live Production Deployment

- **Base Service URL**: [https://jobharvest-backend.onrender.com/](https://jobharvest-backend.onrender.com/)
- **Health Check Endpoint**: [https://jobharvest-backend.onrender.com/actuator/health](https://jobharvest-backend.onrender.com/actuator/health)
- **Job Listing API**: [https://jobharvest-backend.onrender.com/api/jobs](https://jobharvest-backend.onrender.com/api/jobs)
- **Ingestion Audit Trail**: [https://jobharvest-backend.onrender.com/api/ingestion/status](https://jobharvest-backend.onrender.com/api/ingestion/status)

---

## 1. Overview & Problem Statement

Building reliable data pipelines against public job boards requires handling transient network errors, varying data schemas, malformed records, rate limits, and concurrent triggers without missing data or persisting duplicates.

JobHarvest solves this by providing:
- **Resilient Upstream Fetching**: Bounded exponential backoff retries on transient network/5xx failures while failing fast on 429/4xx errors.
- **Data Normalization & Validation**: Truncation of overflowing text, ISO-8601 date parsing, HTML description preservation, and strict required-field validation (`externalId`, `title`, `company`, `jobUrl`, `source`).
- **Database-Backed Rate Limiting**: Cooldown tracking stored directly in PostgreSQL (`ingestion_logs` table) so the 60-minute rate limit survives Render container sleeps, restarts, and redeployments.
- **Race-Safe Concurrency**: Single-flight concurrency locking via `AtomicBoolean` preventing duplicate parallel execution on single-instance runtimes.
- **Hard Deduplication**: Composite database-level `UNIQUE(source, external_id)` constraint backed by application pre-checks.

---

## 2. Features

- **Public Job API Ingestion**: Ingests structured remote job listings from Jobicy's public API without needing authentication or violating terms of service.
- **Data Normalization**: Cleans, trims, and standardizes raw job listings into uniform domain models.
- **Field Validation**: Rejects malformed job entries missing required identifiers or titles.
- **Database Deduplication**: Ignores already-ingested listings based on `(source, external_id)`.
- **PostgreSQL Persistence**: Version-controlled Flyway migrations (`V1__create_schema.sql`).
- **Paginated Job Retrieval**: Search and list jobs with pagination (`page`, `size`) and descending date sorting.
- **Search Filtering**: Filter listings by `keyword` (matches title and company) and `location`.
- **Single Job Details**: Retrieve specific job details by internal primary key ID.
- **Ingestion Audit Log**: Track total fetched, new, duplicate, and failed counts per run with execution duration.
- **Manual & Scheduled Triggers**: Trigger ingestion via `POST /api/ingestion/run` or automated GitHub Actions cron.
- **Spring Actuator Health Monitoring**: Health check endpoint exposing database connection status.

---

## 3. Tech Stack

- **Java**: Java 21 LTS baseline.
- **Framework**: Spring Boot `3.4.3` (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`).
- **Database**: PostgreSQL (Neon Free PostgreSQL in production, H2 in-memory for unit tests).
- **Schema Management**: Flyway (`flyway-core`, `flyway-database-postgresql`).
- **Build Tool**: Apache Maven Wrapper (`./mvnw`).
- **Containerization**: Multi-stage Dockerfile (`eclipse-temurin:21-jdk` & `eclipse-temurin:21-jre`).
- **Web Hosting**: Render Free Web Service (Docker runtime).
- **External Scheduler**: GitHub Actions (`.github/workflows/ingestion.yml`).
- **Upstream Source**: Jobicy Remote Jobs Public API (`https://jobicy.com/api/v2/remote-jobs`).

---

## 4. Architecture & Data Flow

```text
               GitHub Actions Workflow
          (.github/workflows/ingestion.yml)
                          │
                  ~hourly schedule
                          │
                          ▼
               POST /api/ingestion/run
                          │
                          ▼
                Render Free Web Service
               (Docker / Java 21 Runtime)
                          │
                          ▼
            Ingestion Service Orchestrator
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
 DB Cooldown Check   Atomic Lock    Jobicy Adapter
  (ingestion_logs)  (Single-Flight) (RestTemplate Client)
        │                 │                 │
        └─────────────────┼─────────────────┘
                          │
                     Jobicy API
                          │
                          ▼
             Parse → Normalize → Validate
                          │
                          ▼
                     Deduplicate
                (UNIQUE Constraint)
                          │
                          ▼
                 Neon Free PostgreSQL
               (Flyway Schema Migrations)
                          │
                          ▼
                       REST API
                          ▲
                          │
                        Client
```

---

## 5. API Documentation

### Root Endpoint
- **Method**: `GET /`
- **Purpose**: Displays service metadata, operational status, and available API routes.
- **Example Request**:
  ```bash
  curl -i https://jobharvest-backend.onrender.com/
  ```
- **Example Response (`200 OK`)**:
  ```json
  {
    "service": "JobHarvest",
    "description": "Job listing ingestion system — Acdyon Technologies Assignment, Part 1",
    "purpose": "Fetches, normalizes, validates, deduplicates, and persists job listings from public APIs",
    "source": "Jobicy Remote Jobs API (public, no auth)",
    "timestamp": "2026-08-18T13:28:35Z",
    "endpoints": {
      "GET /": "Service information",
      "GET /health": "Health check",
      "GET /api/jobs": "List jobs (keyword, location, page, size)",
      "GET /api/jobs/{id}": "Get single job by internal ID",
      "GET /api/ingestion/status": "Latest ingestion status",
      "POST /api/ingestion/run": "Trigger manual ingestion"
    }
  }
  ```

### Health Check
- **Method**: `GET /health` or `GET /actuator/health`
- **Purpose**: Verifies application health and PostgreSQL database connectivity.
- **Example Response (`200 OK`)**:
  ```json
  {
    "status": "UP",
    "components": {
      "db": {
        "status": "UP",
        "details": {
          "database": "PostgreSQL",
          "validationQuery": "isValid()"
        }
      }
    }
  }
  ```

### List Jobs (Paginated & Filtered)
- **Method**: `GET /api/jobs`
- **Query Parameters**:
  - `keyword` (string, optional): Case-insensitive match on title or company name.
  - `location` (string, optional): Case-insensitive match on job location.
  - `page` (integer, optional, default `0`): Zero-indexed page number.
  - `size` (integer, optional, default `20`, max `100`): Items per page.
- **Example Requests**:
  ```bash
  # Default listing (first 20 jobs)
  curl -i "https://jobharvest-backend.onrender.com/api/jobs"

  # Filter by keyword
  curl -i "https://jobharvest-backend.onrender.com/api/jobs?keyword=developer"

  # Filter by location
  curl -i "https://jobharvest-backend.onrender.com/api/jobs?location=USA"

  # Combined filter with pagination
  curl -i "https://jobharvest-backend.onrender.com/api/jobs?keyword=data&location=USA&page=0&size=10"
  ```
- **Example Response (`200 OK`)**:
  ```json
  {
    "content": [
      {
        "id": 50,
        "externalId": 12845,
        "source": "jobicy",
        "title": "Senior Data Analyst",
        "company": "Counterpart Health",
        "location": "USA",
        "jobUrl": "https://jobicy.com/jobs/12845-senior-data-analyst",
        "jobType": "Full-Time",
        "publishedAt": "2026-08-18T10:00:00Z",
        "fetchedAt": "2026-08-18T13:04:22Z"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 10 },
    "totalElements": 50,
    "totalPages": 5
  }
  ```

### Single Job Details
- **Method**: `GET /api/jobs/{id}`
- **Purpose**: Retrieve full details of a specific job listing by internal ID.
- **Example Request**:
  ```bash
  curl -i "https://jobharvest-backend.onrender.com/api/jobs/50"
  ```
- **Response**: `200 OK` if found, `404 Not Found` if the ID does not exist.

### Ingestion Status
- **Method**: `GET /api/ingestion/status`
- **Purpose**: Returns details of the most recent ingestion run and historical audit logs.
- **Example Response (`200 OK`)**:
  ```json
  {
    "latestIngestion": {
      "status": "SUCCESS",
      "startedAt": "2026-08-18T13:04:22Z",
      "completedAt": "2026-08-18T13:04:32Z",
      "durationMs": 9600,
      "totalFetched": 50,
      "totalNew": 50,
      "totalDuplicates": 0,
      "totalFailed": 0,
      "errorMessage": ""
    },
    "recentHistory": [
      {
        "status": "SUCCESS",
        "startedAt": "2026-08-18T13:04:22Z",
        "totalFetched": 50,
        "totalNew": 50
      }
    ]
  }
  ```

### Manual Ingestion Trigger
- **Method**: `POST /api/ingestion/run`
- **Purpose**: Triggers manual execution of the ingestion pipeline.
- **Possible Responses**:
  - `200 OK`: Ingestion completed (`SUCCESS`, `PARTIAL`, `EMPTY`, or `FAILED`).
  - `429 Too Many Requests`: Upstream 60-minute cooldown active. Returns `Retry-After` header.
  - `409 Conflict`: Another ingestion is currently running on the server.

---

## 6. Database Schema & Flyway Migrations

Database migrations are managed via **Flyway** (`V1__create_schema.sql`).

### `jobs` Table
Stores normalized job listings.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGSERIAL` | `PRIMARY KEY` | Internal auto-increment ID |
| `external_id` | `INTEGER` | `NOT NULL` | External ID assigned by Jobicy |
| `source` | `VARCHAR(50)` | `NOT NULL` | Source identifier (`jobicy`) |
| `title` | `VARCHAR(500)` | `NOT NULL` | Job title |
| `company` | `VARCHAR(300)` | `NOT NULL` | Hiring company name |
| `location` | `VARCHAR(500)` | | Job location |
| `description` | `TEXT` | | Full HTML description |
| `excerpt` | `VARCHAR(2000)` | | Short job summary |
| `job_url` | `VARCHAR(2000)` | `NOT NULL` | Direct application URL |
| `job_type` | `VARCHAR(100)` | | Type (Full-time, Contract, etc.) |
| `job_level` | `VARCHAR(100)` | | Experience level |
| `industry` | `VARCHAR(500)` | | Job industry |
| `salary_min` | `INTEGER` | | Minimum salary |
| `salary_max` | `INTEGER` | | Maximum salary |
| `salary_currency` | `VARCHAR(10)` | | Currency code (USD, EUR) |
| `published_at` | `TIMESTAMP` | | Date listing was posted |
| `fetched_at` | `TIMESTAMP` | `NOT NULL` | Ingestion timestamp |
| `created_at` | `TIMESTAMP` | `NOT NULL` | Record creation timestamp |

**Indexes & Constraints**:
- `CONSTRAINT uq_source_external_id UNIQUE (source, external_id)`: Enforces database-level deduplication.
- `CREATE INDEX idx_jobs_source ON jobs(source)`
- `CREATE INDEX idx_jobs_title ON jobs(title)`

### `ingestion_logs` Table
Audit trail of every ingestion execution.

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGSERIAL` | `PRIMARY KEY` |
| `source` | `VARCHAR(50)` | Source name |
| `status` | `VARCHAR(20)` | `RUNNING`, `SUCCESS`, `PARTIAL`, `EMPTY`, `FAILED`, `RATE_LIMITED` |
| `started_at` | `TIMESTAMP` | Ingestion start time |
| `completed_at` | `TIMESTAMP` | Completion time |
| `duration_ms` | `BIGINT` | Total processing duration in milliseconds |
| `total_fetched` | `INTEGER` | Total raw records returned |
| `total_new` | `INTEGER` | Total new records saved |
| `total_duplicates` | `INTEGER` | Total duplicate records skipped |
| `total_failed` | `INTEGER` | Total malformed records skipped |
| `error_message` | `TEXT` | Error details if failed |

---

## 7. Ingestion Pipeline Details & Production Results

### Ingestion Execution Lifecycle
1. **Database Cooldown Check**: Queries `ingestion_logs` for the latest `started_at` timestamp. If `< 60 minutes` ago, aborts fetch and returns `429 RATE_LIMITED`.
2. **Concurrency Acquisition**: Acquires non-blocking `AtomicBoolean` lock. Returns `409 ALREADY_RUNNING` if another thread is executing.
3. **Double-Check Cooldown**: Re-checks database cooldown under lock to prevent race conditions.
4. **Record Attempt**: Saves `IngestionLog(status="RUNNING")` to PostgreSQL before sending HTTP requests.
5. **HTTP Fetch & Retries**: Calls Jobicy API using `RestTemplate`. Retries up to 3 times on 5xx or connection timeouts with exponential backoff (`2s`, `4s`, `8s`). Fails fast on 429/4xx errors.
6. **Normalization & Validation**: Truncates text fields, parses dates, and enforces required fields (`externalId`, `title`, `company`, `jobUrl`, `source`).
7. **Deduplication & Persistence**: Checks `existsBySourceAndExternalId()`. Saves new jobs; handles concurrent insert races via `DataIntegrityViolationException`.
8. **Final Log Completion**: Updates `IngestionLog` with final status (`SUCCESS`, `PARTIAL`, `EMPTY`, or `FAILED`).

### Verified Production Ingestion Results
- **Status**: `SUCCESS`
- **Total Fetched**: 50
- **Total New**: 50
- **Total Duplicates**: 0
- **Total Failed**: 0
- **Duration**: 9,600 ms

---

## 8. Local Setup Instructions

### Prerequisites
- Java 21 JDK (or JDK 25)
- Git

### 1. Clone Repository
```bash
git clone https://github.com/suryansh79/jobharvest-backend.git
cd jobharvest-backend
```

### 2. Configure Environment Variables (Optional for Local)
Copy `.env.example` to set local database overrides, or run with defaults (uses H2 in-memory database for local testing).

### 3. Run Automated Unit & Integration Tests
```bash
./mvnw test
```
*Executes all 21 automated unit tests using H2 database and mocked HTTP servers.*

### 4. Run Application Locally
```bash
./mvnw spring-boot:run
```
Application will start on `http://localhost:8080`.

---

## 9. Environment Variables Reference

| Environment Variable | Description | Default Value (Local) |
|----------------------|-------------|-----------------------|
| `PORT` | HTTP server port | `8080` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/jobharvest` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `postgres` |
| `APP_INGESTION_SOURCE_URL` | Upstream Jobicy API URL | `https://jobicy.com/api/v2/remote-jobs?count=50` |
| `APP_INGESTION_COOLDOWN_MINUTES` | Upstream rate-limit cooldown | `60` |
| `APP_INGESTION_MAX_RETRIES` | Upstream HTTP retry count | `3` |
| `APP_INGESTION_BACKOFF_BASE_MS` | Exponential backoff base ms | `2000` |
| `APP_INGESTION_TIMEOUT_SECONDS` | HTTP connect/read timeout | `30` |

---

## 10. Production Deployment Architecture (Render + Neon)

1. **Neon Free PostgreSQL**: Hosts non-expiring PostgreSQL instance (`neondb`).
2. **Render Free Web Service**: Runs multi-stage Docker build (`Dockerfile`) with 750 free monthly instance hours.
3. **Render Environment Configuration**:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://<neon-host>/neondb?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME` = `<username>`
   - `SPRING_DATASOURCE_PASSWORD` = `<password>`
4. **GitHub Actions Scheduler**: `.github/workflows/ingestion.yml` runs hourly, making an HTTP `POST` request to `${{ secrets.RENDER_URL }}/api/ingestion/run`. Wakes Render instance from sleep and triggers ingestion cleanly.
