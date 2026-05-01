# Local LLM Knowledge Base Baseline

Date: 2026-05-01

## Decision

SchemaPilot treats local LLM plus local knowledge base as the default private-deployment target.

Cloud LLM providers are optional adapters and must be explicitly enabled. They are not the default path for prompts, RAG, embeddings, migration evidence, or project knowledge.

## Runtime Shape

- Local LLM endpoint: OpenAI-compatible HTTP endpoint, defaulting to `http://localhost:11434`.
- Local model default: `qwen2.5-coder:latest`.
- Local knowledge base: `KnowledgeService` with built-in rules, reviewed historical cases, project summaries, and local embedding recall.
- Vector storage target: local PostgreSQL with pgvector when available.
- Safe fallback: in-memory `KnowledgeRepository` plus `LocalEmbeddingAdapter`.
- Cloud provider: disabled by default and only receives redacted local RAG context when explicitly configured.

## Guardrails

- Prompts must be built from redacted project context and cited knowledge chunks.
- Passwords, JDBC URLs, tokens, and API keys must not enter prompts, embeddings, logs, audit records, or knowledge chunks.
- Local LLM suggestions remain advisory and cannot freeze SQL baselines, approve reports, or execute migration steps.
- External MCP clients and cloud providers remain opt-in for private deployments.

## Implementation Markers

- `AiGovernanceRegistry` lists `local-openai-compatible` first and marks it as `preferredForPrivateDeployment`.
- `AiProviderConfig.knowledgeMode` records whether a provider uses `local-rag` or redacted local context.
- `LocalFirstAiProvider` is the primary `AiProvider`: it calls the local OpenAI-compatible endpoint when `schemapilot.ai.enabled=true` and falls back to `MockAiProvider` when local inference is disabled, unavailable, or returns an invalid response.
- `LocalLlmClient` calls `/v1/chat/completions` and sends only redacted context plus cited local knowledge excerpts.
- `application.yml` declares `schemapilot.ai.mode=local-first`.
- `KnowledgeService.multiRecall` keeps lexical, metadata, and local embedding recall inside the application boundary.

## P3 Hardening Backlog

- Add a pgvector-backed `KnowledgeRepository` or Spring AI PgVectorStore adapter without changing `KnowledgeService` callers.
- Add local LLM health checks, model discovery, timeout metrics, and fallback counters.
- Add UI status for local LLM endpoint health, selected local model, knowledge hit rate, and cloud disabled/enabled state.

These items are not required for the current P0/P1/P2 prototype closure. They are tracked as P3 production hardening tasks in `docs/task.md`.
