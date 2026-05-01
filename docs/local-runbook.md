# Local Runbook

## Runtime Targets

- Backend: `http://127.0.0.1:8080`
- Frontend: `http://127.0.0.1:5173`
- Metadata database: PostgreSQL on `127.0.0.1:5432`
- Local LLM endpoint: `http://127.0.0.1:11434`

## Start Backend

```powershell
cd D:\source\SchemaPilot\backend
$env:SCHEMAPILOT_DB_URL='jdbc:postgresql://127.0.0.1:5432/schemapilot'
$env:SCHEMAPILOT_DB_USERNAME='schemapilot'
$env:SCHEMAPILOT_DB_PASSWORD='schemapilot'
$env:SCHEMAPILOT_KNOWLEDGE_REPOSITORY='pgvector'
$env:SCHEMAPILOT_AI_ENABLED='true'
$env:SCHEMAPILOT_LOCAL_LLM_ENDPOINT='http://127.0.0.1:11434'
.\gradlew.bat bootRun
```

## Start Frontend

```powershell
cd D:\source\SchemaPilot\frontend
npm run dev -- --host 127.0.0.1 --port 5173
```

## PostgreSQL + pgvector Checks

```powershell
$env:PGPASSWORD='schemapilot'
& 'C:\Program Files\PostgreSQL\15\pgAdmin 4\runtime\psql.exe' `
  -h 127.0.0.1 -p 5432 -U schemapilot -d schemapilot `
  -c "select extname, extversion from pg_extension where extname = 'vector';"
```

Expected result:

```text
vector | 0.8.0
```

## Health Checks

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/api/ai/local-status
Invoke-RestMethod http://127.0.0.1:8080/api/knowledge/metrics
```

`local-status.reachable=false` is acceptable when no local LLM server is running. In that case SchemaPilot falls back to the mock provider and increments `fallbackCount` after AI requests.

## Test Commands

```powershell
cd D:\source\SchemaPilot\backend
.\gradlew.bat test

cd D:\source\SchemaPilot\frontend
npm run lint
npm run build
```

Optional pgvector integration test:

```powershell
cd D:\source\SchemaPilot\backend
$env:SCHEMAPILOT_IT_PGVECTOR='true'
$env:SCHEMAPILOT_IT_PG_URL='jdbc:postgresql://127.0.0.1:5432/schemapilot'
$env:SCHEMAPILOT_IT_PG_USERNAME='schemapilot'
$env:SCHEMAPILOT_IT_PG_PASSWORD='schemapilot'
.\gradlew.bat test --tests org.sainm.schemapilot.integration.PgVectorEnvironmentIntegrationTest
```
