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
- AI/RAG: Spring AI, MCP, PostgreSQL + pgvector.
- Frontend: React, TypeScript, Vite, Ant Design, TanStack Query, Monaco, React Flow, ECharts.

## Local Development

### Backend

The backend is configured for Java 25 toolchains.

```powershell
cd backend
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

```powershell
docker compose up -d
```

The default metadata database is:

```text
jdbc:postgresql://localhost:5432/schemapilot
username: schemapilot
password: schemapilot
```

