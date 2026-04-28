create table if not exists project (
    id uuid primary key,
    name varchar(128) not null,
    description text,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists audit_log (
    id uuid primary key,
    project_id uuid,
    actor varchar(128),
    action varchar(128) not null,
    target_type varchar(64),
    target_id varchar(128),
    detail jsonb,
    created_at timestamptz not null
);
