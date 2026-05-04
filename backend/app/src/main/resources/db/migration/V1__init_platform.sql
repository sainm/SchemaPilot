create table if not exists schema_pilot_schema_history_marker (
    id bigint generated always as identity primary key,
    marker varchar(64) not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists migration_project (
    id uuid primary key,
    name varchar(200) not null,
    description text,
    status varchar(40) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists source_project (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    name varchar(200) not null,
    type varchar(40) not null,
    created_at timestamp not null
);

create table if not exists input_batch (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    source_project_id uuid not null references source_project(id),
    status varchar(40) not null,
    created_at timestamp not null
);

create table if not exists input_source (
    id uuid primary key,
    batch_id uuid not null references input_batch(id),
    project_id uuid not null references migration_project(id),
    source_project_id uuid not null references source_project(id),
    type varchar(40) not null,
    name varchar(255) not null,
    relative_path text not null,
    content_hash varchar(64) not null,
    size_bytes bigint not null,
    encoding varchar(80) not null,
    original_text text,
    created_at timestamp not null
);

create index if not exists idx_source_project_project_id on source_project(project_id);
create index if not exists idx_input_batch_project_id on input_batch(project_id);
create index if not exists idx_input_source_batch_id on input_source(batch_id);

create table if not exists db_object (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    source_project_id uuid not null references source_project(id),
    input_source_id uuid not null references input_source(id),
    type varchar(40) not null,
    schema_name varchar(255),
    object_name varchar(255) not null,
    status varchar(40) not null,
    source_path text,
    start_line integer not null,
    end_line integer not null,
    start_offset integer not null,
    end_offset integer not null,
    original_sql text not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table if not exists db_column (
    id uuid primary key,
    object_id uuid not null references db_object(id),
    column_name varchar(255) not null,
    source_type varchar(255),
    target_type varchar(255),
    nullable boolean not null default true,
    ordinal_position integer
);

create table if not exists parse_issue (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    source_project_id uuid not null references source_project(id),
    input_source_id uuid not null references input_source(id),
    code varchar(80) not null,
    message text not null,
    source_path text,
    start_line integer not null,
    end_line integer not null,
    start_offset integer not null,
    end_offset integer not null,
    original_text text not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists risk_issue (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    source_project_id uuid not null references source_project(id),
    input_source_id uuid not null references input_source(id),
    object_id uuid not null references db_object(id),
    code varchar(120) not null,
    level varchar(40) not null,
    message text not null,
    evidence text,
    rule_code varchar(120),
    created_at timestamp not null default current_timestamp
);

create table if not exists conversion_result (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    source_project_id uuid not null references source_project(id),
    input_source_id uuid not null references input_source(id),
    object_id uuid not null references db_object(id),
    source_sql text not null,
    target_sql text not null,
    level varchar(40) not null,
    notes text,
    created_at timestamp not null default current_timestamp
);

create table if not exists sql_version (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    source_project_id uuid not null references source_project(id),
    input_source_id uuid not null references input_source(id),
    object_id uuid not null references db_object(id),
    conversion_result_id uuid not null references conversion_result(id),
    parent_version_id uuid references sql_version(id),
    version_number integer not null,
    source varchar(40) not null,
    status varchar(40) not null,
    source_sql text not null,
    target_sql text not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists object_dependency (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    source_project_id uuid not null references source_project(id),
    source_object_id uuid not null references db_object(id),
    target_object_id uuid not null references db_object(id),
    type varchar(80) not null,
    evidence text,
    created_at timestamp not null default current_timestamp
);

create table if not exists stage_report (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    type varchar(40) not null,
    status varchar(40) not null,
    title varchar(200) not null,
    json_content text not null,
    html_content text not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists precheck_report (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    stage_report_id uuid not null references stage_report(id),
    status varchar(40) not null,
    object_count integer not null,
    parse_issue_count integer not null,
    risk_count integer not null,
    blocker_count integer not null,
    high_risk_count integer not null,
    conversion_count integer not null,
    sql_version_count integer not null
);

create table if not exists review_record (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    report_id uuid not null references stage_report(id),
    decision varchar(40) not null,
    reviewer varchar(200) not null,
    comment text not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists ai_suggestion (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    type varchar(40) not null,
    status varchar(40) not null,
    title varchar(200) not null,
    summary text not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists ai_call_log (
    id uuid primary key,
    project_id uuid not null references migration_project(id),
    provider varchar(120) not null,
    type varchar(40) not null,
    object_count integer not null,
    risk_count integer not null,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_db_object_project_id on db_object(project_id);
create index if not exists idx_db_object_source_project_id on db_object(source_project_id);
create index if not exists idx_db_object_input_source_id on db_object(input_source_id);
create index if not exists idx_parse_issue_project_id on parse_issue(project_id);
create index if not exists idx_parse_issue_input_source_id on parse_issue(input_source_id);
create index if not exists idx_risk_issue_project_id on risk_issue(project_id);
create index if not exists idx_risk_issue_object_id on risk_issue(object_id);
create index if not exists idx_conversion_result_project_id on conversion_result(project_id);
create index if not exists idx_conversion_result_object_id on conversion_result(object_id);
create index if not exists idx_sql_version_project_id on sql_version(project_id);
create index if not exists idx_sql_version_object_id on sql_version(object_id);
create index if not exists idx_object_dependency_project_id on object_dependency(project_id);
create index if not exists idx_object_dependency_source_object_id on object_dependency(source_object_id);
create index if not exists idx_object_dependency_target_object_id on object_dependency(target_object_id);
create index if not exists idx_stage_report_project_id on stage_report(project_id);
create index if not exists idx_precheck_report_project_id on precheck_report(project_id);
create index if not exists idx_review_record_project_id on review_record(project_id);
create index if not exists idx_review_record_report_id on review_record(report_id);
create index if not exists idx_ai_suggestion_project_id on ai_suggestion(project_id);
create index if not exists idx_ai_call_log_project_id on ai_call_log(project_id);
