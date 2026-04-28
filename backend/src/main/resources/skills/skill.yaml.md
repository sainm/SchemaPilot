# SchemaPilot skill.yaml format

```yaml
id: oracle-table-ddl
version: 1.0.0
status: ENABLED
description: Convert a single Oracle CREATE TABLE statement into PostgreSQL DDL draft.
allowedTools:
  - sql.analyze
requiresReview: false
outputSchema:
  objectType: string
  objectName: string
  targetSql: string
  risks: array
fixtures:
  - name: create-table-users
    input: fixtures/manual-sql/create_table_users.sql
    expected:
      objectType: TABLE
      valid: true
```

Rules:

- `id` and `version` identify a stable executable skill.
- `allowedTools` is the only tool surface a skill may call.
- `requiresReview=true` means the result cannot become a SQL baseline without human review.
- `outputSchema` lists required output fields used by `SkillExecutor` schema validation.
- Fixtures should cover at least one successful output and one review-required output for risky skills.
