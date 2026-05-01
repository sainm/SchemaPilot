# SchemaPilot

SchemaPilot is a Java-first Oracle to PostgreSQL migration platform.

The first delivery loop is:

```text
Input SQL / file
  -> object recognition
  -> conversion
  -> risk detection
  -> AI suggestion
  -> precheck report
  -> review
  -> baseline SQL package
```

## Stack

- Backend: Java 25, Gradle, Spring Boot 4, Spring MVC, jOOQ, Flyway.
- Migration runtime: virtual threads, pgJDBC CopyManager, Java FFM for controlled off-heap buffers.
- AI/RAG: local OpenAI-compatible LLM provider, mock fallback, MCP prototype, PostgreSQL + pgvector knowledge repository.
- Frontend: React, TypeScript, Vite, Ant Design, TanStack Query, Monaco, React Flow, ECharts.

## Local Development

### Backend

The backend is configured for Java 25 toolchains.

```powershell
cd backend
$env:SCHEMAPILOT_DB_URL='jdbc:postgresql://127.0.0.1:5432/schemapilot'
$env:SCHEMAPILOT_DB_USERNAME='schemapilot'
$env:SCHEMAPILOT_DB_PASSWORD='schemapilot'
$env:SCHEMAPILOT_KNOWLEDGE_REPOSITORY='pgvector'
.\gradlew.bat bootRun
```

Current machine note: install or point `JAVA_HOME` to a Java 25 JDK before building the backend.

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080`.

### Metadata Database

Option A: Docker Compose:

```powershell
docker compose up -d
```

Option B: WSL PostgreSQL 17 with pgvector:

```bash
sudo apt-get update
sudo apt-get install -y postgresql-17-pgvector
PGPASSWORD=<postgres-admin-password> psql -h 127.0.0.1 -U postgres -d schemapilot -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

The default development metadata database is:

```text
jdbc:postgresql://127.0.0.1:5432/schemapilot
username: schemapilot
password: schemapilot
```

### Local LLM

SchemaPilot can call a local OpenAI-compatible endpoint. When the endpoint is unavailable, backend requests fall back to the mock provider and expose the state at:

```text
GET /api/ai/local-status
```

Default local endpoint:

```text
http://127.0.0.1:11434
model: qwen2.5-coder:latest
```

### Verification

```powershell
cd backend
.\gradlew.bat test

cd ..\frontend
npm run lint
npm run build
```

Optional real pgvector integration test:

```powershell
cd backend
$env:SCHEMAPILOT_IT_PGVECTOR='true'
$env:SCHEMAPILOT_IT_PG_URL='jdbc:postgresql://127.0.0.1:5432/schemapilot'
$env:SCHEMAPILOT_IT_PG_USERNAME='schemapilot'
$env:SCHEMAPILOT_IT_PG_PASSWORD='schemapilot'
.\gradlew.bat test --tests org.sainm.schemapilot.integration.PgVectorEnvironmentIntegrationTest
```
