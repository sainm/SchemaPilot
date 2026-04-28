create table if not exists precheck_report (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    status varchar(32) not null,
    report_version integer not null,
    summary jsonb,
    input_hash varchar(128),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists review_record (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    report_id uuid references precheck_report(id) on delete set null,
    decision varchar(32) not null,
    reviewer varchar(128),
    comment text,
    created_at timestamptz not null
);

create table if not exists artifact_version (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    artifact_type varchar(64) not null,
    artifact_id varchar(128) not null,
    version integer not null,
    input_hash varchar(128),
    metadata jsonb,
    created_at timestamptz not null
);

create table if not exists ai_provider_config (
    id uuid primary key,
    provider_type varchar(64) not null,
    endpoint varchar(512),
    model_name varchar(128),
    encrypted_api_key text,
    enabled boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists prompt_template (
    id uuid primary key,
    code varchar(128) not null,
    version integer not null,
    purpose varchar(128) not null,
    template_text text not null,
    created_at timestamptz not null,
    unique (code, version)
);

create table if not exists ai_suggestion (
    id uuid primary key,
    project_id uuid references project(id) on delete cascade,
    db_object_id uuid references db_object(id) on delete cascade,
    suggestion_type varchar(64) not null,
    prompt_version varchar(64),
    model_name varchar(128),
    input_hash varchar(128),
    output_text text not null,
    status varchar(32) not null,
    accepted_by varchar(128),
    accepted_at timestamptz,
    created_at timestamptz not null
);

create table if not exists ai_conversation (
    id uuid primary key,
    project_id uuid references project(id) on delete cascade,
    db_object_id uuid references db_object(id) on delete cascade,
    title varchar(256) not null,
    created_by varchar(128),
    created_at timestamptz not null
);

create table if not exists ai_message (
    id uuid primary key,
    conversation_id uuid not null references ai_conversation(id) on delete cascade,
    role varchar(32) not null,
    content text not null,
    created_at timestamptz not null
);

create table if not exists agent_run (
    id uuid primary key,
    project_id uuid references project(id) on delete cascade,
    agent_type varchar(64) not null,
    trigger_type varchar(64) not null,
    input_ref varchar(256),
    status varchar(32) not null,
    started_at timestamptz not null,
    finished_at timestamptz
);

create table if not exists agent_step (
    id uuid primary key,
    run_id uuid not null references agent_run(id) on delete cascade,
    step_type varchar(64) not null,
    tool_name varchar(128),
    input_hash varchar(128),
    output_ref varchar(256),
    status varchar(32) not null,
    created_at timestamptz not null
);

create table if not exists skill_definition (
    id uuid primary key,
    code varchar(128) not null,
    version varchar(32) not null,
    description text,
    requires_review boolean not null default true,
    descriptor jsonb not null,
    created_at timestamptz not null,
    unique (code, version)
);

create table if not exists skill_run (
    id uuid primary key,
    project_id uuid references project(id) on delete cascade,
    skill_code varchar(128) not null,
    skill_version varchar(32) not null,
    input_hash varchar(128),
    output_ref varchar(256),
    status varchar(32) not null,
    created_at timestamptz not null
);

create table if not exists mcp_tool_call (
    id uuid primary key,
    project_id uuid references project(id) on delete cascade,
    tool_name varchar(128) not null,
    input_hash varchar(128),
    status varchar(32) not null,
    duration_ms bigint,
    created_at timestamptz not null
);

create table if not exists knowledge_document (
    id uuid primary key,
    source_type varchar(64) not null,
    title varchar(256) not null,
    source_uri varchar(1024),
    version varchar(64),
    owner varchar(128),
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create extension if not exists vector;

create table if not exists knowledge_chunk (
    id uuid primary key,
    document_id uuid not null references knowledge_document(id) on delete cascade,
    chunk_type varchar(64) not null,
    content text not null,
    metadata jsonb,
    content_hash varchar(128) not null,
    embedding vector(1536),
    created_at timestamptz not null
);

create index if not exists idx_precheck_report_project on precheck_report(project_id);
create index if not exists idx_review_record_project on review_record(project_id);
create index if not exists idx_ai_suggestion_project on ai_suggestion(project_id);
create index if not exists idx_agent_run_project on agent_run(project_id);
create index if not exists idx_skill_run_project on skill_run(project_id);
create index if not exists idx_knowledge_chunk_document on knowledge_chunk(document_id);
