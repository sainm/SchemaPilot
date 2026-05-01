alter table knowledge_chunk
    add column if not exists chunk_key varchar(256),
    add column if not exists title varchar(512),
    add column if not exists document_type varchar(64),
    add column if not exists source varchar(1024),
    add column if not exists version_number integer,
    add column if not exists status varchar(32),
    add column if not exists local_embedding vector(16);

update knowledge_chunk
set chunk_key = coalesce(chunk_key, content_hash),
    title = coalesce(title, left(content, 120)),
    document_type = coalesce(document_type, chunk_type),
    source = coalesce(source, 'legacy-knowledge-chunk'),
    version_number = coalesce(version_number, 1),
    status = coalesce(status, 'ACTIVE')
where chunk_key is null
   or title is null
   or document_type is null
   or source is null
   or version_number is null
   or status is null;

alter table knowledge_chunk
    alter column chunk_key set not null,
    alter column title set not null,
    alter column document_type set not null,
    alter column source set not null,
    alter column version_number set not null,
    alter column status set not null;

create index if not exists idx_knowledge_chunk_key on knowledge_chunk(chunk_key);
create index if not exists idx_knowledge_chunk_metadata on knowledge_chunk using gin(metadata);
create index if not exists idx_knowledge_chunk_local_embedding
    on knowledge_chunk using hnsw (local_embedding vector_cosine_ops);
