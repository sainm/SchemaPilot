create table if not exists input_source (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    source_type varchar(32) not null,
    name varchar(160) not null,
    status varchar(32) not null,
    content_hash varchar(128),
    original_filename varchar(512),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists db_object (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    input_source_id uuid references input_source(id) on delete set null,
    object_type varchar(48) not null,
    object_name varchar(256) not null,
    schema_name varchar(256),
    original_sql text,
    parse_status varchar(32) not null,
    conversion_status varchar(32) not null,
    risk_level varchar(32) not null,
    model_json jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists parse_issue (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    db_object_id uuid references db_object(id) on delete cascade,
    severity varchar(32) not null,
    message text not null,
    line_start integer,
    line_end integer,
    created_at timestamptz not null
);

create table if not exists risk_issue (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    db_object_id uuid references db_object(id) on delete cascade,
    risk_type varchar(64) not null,
    risk_level varchar(32) not null,
    message text not null,
    suggestion text,
    created_at timestamptz not null
);

create table if not exists conversion_result (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    db_object_id uuid not null references db_object(id) on delete cascade,
    conversion_level varchar(32) not null,
    generated_sql text,
    edited_sql text,
    status varchar(32) not null,
    input_hash varchar(128),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists sql_version (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    db_object_id uuid references db_object(id) on delete cascade,
    source varchar(32) not null,
    status varchar(32) not null,
    sql_text text not null,
    input_hash varchar(128),
    created_at timestamptz not null
);

create table if not exists work_item (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    db_object_id uuid references db_object(id) on delete cascade,
    source varchar(64) not null,
    title varchar(256) not null,
    severity varchar(32) not null,
    status varchar(32) not null,
    suggested_action text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_input_source_project on input_source(project_id);
create index if not exists idx_db_object_project on db_object(project_id);
create index if not exists idx_risk_issue_project on risk_issue(project_id);
create index if not exists idx_conversion_result_project on conversion_result(project_id);
create index if not exists idx_work_item_project on work_item(project_id);
