# SchemaPilot Coding Guidelines

## Backend

- Use Java 25.
- Use Gradle Kotlin DSL.
- Keep the backend Java-first for the MVP.
- Prefer Spring MVC with virtual threads over WebFlux.
- Keep SQL migration execution behind review gates and frozen SQL baselines.
- Use FFM only for controlled large buffers such as COPY encoding, LOB streaming, large file parsing, and checksum chunks.
- Do not put passwords, JDBC URLs, or secrets into prompts, embeddings, logs, or audit details.

## Frontend

- Use React, TypeScript, Vite, Ant Design, and ProComponents.
- Build the actual migration workbench experience, not a marketing landing page.
- Keep dense operational screens readable: tables, filters, status tags, diffs, and review actions should be first-class.
- Use Monaco for SQL editing and diff views.
- Use React Flow for dependency and migration plan graphs.

## AI

- AI is a copilot, not an executor.
- AI suggestions must be accepted, ignored, or edited by a user.
- AI must not freeze baselines, approve reviews, or execute SQL.
- RAG answers must preserve knowledge source references.

