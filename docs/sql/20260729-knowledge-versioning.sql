begin;

create sequence if not exists kb_document_version_migration_seq start with 1 increment by 1;
create sequence if not exists kb_index_generation_migration_seq start with 1 increment by 1;

alter table kb_document add column if not exists source_key varchar(512);
alter table kb_document add column if not exists raw_file_hash varchar(64);
alter table kb_document add column if not exists normalized_content_hash varchar(64);
alter table kb_document add column if not exists active_version_id bigint;
alter table kb_document add column if not exists pending_version_id bigint;

update kb_document
set source_key = left(case
    when source_url is not null and btrim(source_url) <> '' then 'URL:' || replace(btrim(source_url), chr(92), '/')
    when object_key is not null and btrim(object_key) <> '' then 'OBJECT:' || replace(btrim(object_key), chr(92), '/')
    else 'MANUAL:' || id::text
end, 512)
where source_key is null or btrim(source_key) = '';

update kb_document
set normalized_content_hash = coalesce(nullif(index_hash, ''), md5(coalesce(content, '')))
where normalized_content_hash is null or btrim(normalized_content_hash) = '';

do $$
begin
    if exists (
        select 1
        from kb_document
        where deleted = false
        group by tenant_id, source_key
        having count(*) > 1
    ) then
        raise exception '知识文档存在重复 source_key，请先手工处理后再执行迁移';
    end if;
end
$$;

alter table kb_document alter column source_key set not null;
alter table kb_document alter column normalized_content_hash set not null;

create unique index if not exists uk_kb_document_source_active
    on kb_document (tenant_id, source_key)
    where deleted = false;
create index if not exists idx_kb_document_source
    on kb_document (tenant_id, source_key);
create index if not exists idx_kb_document_active_version
    on kb_document (tenant_id, active_version_id, deleted);

create table if not exists kb_document_version (
    id bigint primary key,
    tenant_id bigint not null,
    document_id bigint not null,
    version_no integer not null,
    source_key varchar(512) not null,
    raw_file_hash varchar(64),
    normalized_content_hash varchar(64) not null,
    build_fingerprint varchar(64) not null,
    status varchar(32) not null,
    title varchar(128) not null,
    source_type varchar(128),
    category varchar(128),
    tags varchar(512),
    source_url varchar(512),
    object_key varchar(512),
    content_snapshot text not null,
    chunk_count integer,
    vector_dimension integer,
    embedding_model varchar(128),
    error_message varchar(512),
    ready_at timestamp,
    activated_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_kb_document_version_no unique (tenant_id, document_id, version_no)
);

create index if not exists idx_kb_document_version_document
    on kb_document_version (tenant_id, document_id);
create index if not exists idx_kb_document_version_status
    on kb_document_version (tenant_id, status);

insert into kb_document_version (
    id,
    tenant_id,
    document_id,
    version_no,
    source_key,
    raw_file_hash,
    normalized_content_hash,
    build_fingerprint,
    status,
    title,
    source_type,
    category,
    tags,
    source_url,
    object_key,
    content_snapshot,
    chunk_count,
    vector_dimension,
    embedding_model,
    ready_at,
    activated_at,
    created_at,
    updated_at
)
select
    nextval('kb_document_version_migration_seq'),
    document.tenant_id,
    document.id,
    greatest(coalesce(document.index_version, 1), 1),
    document.source_key,
    document.raw_file_hash,
    document.normalized_content_hash,
    coalesce(nullif(document.index_hash, ''), md5(coalesce(document.content, ''))),
    case when document.deleted then 'RETIRED' else 'ACTIVE' end,
    document.title,
    document.source_type,
    document.category,
    document.tags,
    document.source_url,
    document.object_key,
    coalesce(document.content, ''),
    coalesce(document.chunk_count, 0),
    document.vector_dimension,
    document.embedding_model,
    document.indexed_at,
    document.indexed_at,
    document.created_at,
    document.updated_at
from kb_document document
where not exists (
    select 1
    from kb_document_version version
    where version.tenant_id = document.tenant_id
      and version.document_id = document.id
);

update kb_document document
set active_version_id = version.id
from kb_document_version version
where version.tenant_id = document.tenant_id
  and version.document_id = document.id
  and version.version_no = greatest(coalesce(document.index_version, 1), 1)
  and document.active_version_id is null;

create table if not exists kb_index_generation (
    id bigint primary key,
    tenant_id bigint not null,
    status varchar(32) not null,
    elasticsearch_index varchar(128) not null,
    milvus_collection varchar(128) not null,
    embedding_model varchar(128) not null,
    vector_dimension integer,
    chunk_profile_hash varchar(64) not null,
    snapshot_outbox_id bigint,
    replayed_outbox_id bigint,
    document_count integer,
    completed_document_count integer,
    progress integer,
    message varchar(512),
    error_message varchar(1024),
    activated_at timestamp,
    finished_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_kb_index_generation_status
    on kb_index_generation (tenant_id, status);
create unique index if not exists uk_kb_index_generation_active
    on kb_index_generation (tenant_id)
    where status = 'ACTIVE';
create unique index if not exists uk_kb_index_generation_rebuilding
    on kb_index_generation (tenant_id)
    where status in ('BUILDING', 'CATCHING_UP', 'READY');

insert into kb_index_generation (
    id,
    tenant_id,
    status,
    elasticsearch_index,
    milvus_collection,
    embedding_model,
    vector_dimension,
    chunk_profile_hash,
    document_count,
    completed_document_count,
    progress,
    message,
    activated_at,
    finished_at,
    created_at,
    updated_at
)
select
    nextval('kb_index_generation_migration_seq'),
    tenant.tenant_id,
    'ACTIVE',
    'crm_knowledge_chunk',
    'crm_knowledge_chunk',
    coalesce((
        select max(document.embedding_model)
        from kb_document document
        where document.tenant_id = tenant.tenant_id
    ), 'LEGACY'),
    (
        select max(document.vector_dimension)
        from kb_document document
        where document.tenant_id = tenant.tenant_id
    ),
    md5('legacy'),
    (
        select count(*)
        from kb_document document
        where document.tenant_id = tenant.tenant_id
          and document.deleted = false
          and document.active_version_id is not null
    ),
    (
        select count(*)
        from kb_document document
        where document.tenant_id = tenant.tenant_id
          and document.deleted = false
          and document.active_version_id is not null
    ),
    100,
    '历史索引代次已接管',
    current_timestamp,
    current_timestamp,
    current_timestamp,
    current_timestamp
from (
    select tenant_id from kb_document
    union
    select tenant_id from kb_chunk
) tenant
where not exists (
    select 1
    from kb_index_generation generation
    where generation.tenant_id = tenant.tenant_id
      and generation.status = 'ACTIVE'
);

alter table kb_chunk add column if not exists document_version_id bigint;
alter table kb_chunk add column if not exists index_generation_id bigint;

update kb_chunk chunk
set document_version_id = document.active_version_id
from kb_document document
where document.tenant_id = chunk.tenant_id
  and document.id = chunk.document_id
  and chunk.document_version_id is null;

update kb_chunk chunk
set index_generation_id = generation.id
from kb_index_generation generation
where generation.tenant_id = chunk.tenant_id
  and generation.status = 'ACTIVE'
  and chunk.index_generation_id is null;

do $$
begin
    if exists (
        select 1
        from kb_chunk
        where document_version_id is null
           or index_generation_id is null
    ) then
        raise exception '知识分片存在无法关联文档版本或索引代次的记录，请先手工处理后再执行迁移';
    end if;
end
$$;

alter table kb_chunk alter column document_version_id set not null;
alter table kb_chunk alter column index_generation_id set not null;

create index if not exists idx_kb_chunk_active_search
    on kb_chunk (tenant_id, index_generation_id, document_id, document_version_id, deleted);

alter table kb_ingest_task add column if not exists document_version_id bigint;
alter table kb_ingest_task add column if not exists index_generation_id bigint;
alter table kb_ingest_task add column if not exists idempotency_key varchar(64);

update kb_ingest_task task
set document_version_id = document.active_version_id
from kb_document document
where document.tenant_id = task.tenant_id
  and document.id = task.document_id
  and task.document_version_id is null;

update kb_ingest_task task
set index_generation_id = generation.id
from kb_index_generation generation
where generation.tenant_id = task.tenant_id
  and generation.status = 'ACTIVE'
  and task.index_generation_id is null;

create index if not exists idx_kb_ingest_task_idempotency
    on kb_ingest_task (tenant_id, document_id, idempotency_key, status);

create table if not exists kb_change_outbox (
    id bigint primary key,
    tenant_id bigint not null,
    document_id bigint not null,
    document_version_id bigint,
    source_index_generation_id bigint,
    event_type varchar(32) not null,
    event_key varchar(64) not null,
    payload_json text not null,
    published boolean not null,
    publish_attempts integer,
    error_message varchar(512),
    published_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

alter table kb_change_outbox
    add column if not exists source_index_generation_id bigint;

create index if not exists idx_kb_change_outbox_publish
    on kb_change_outbox (published, created_at);
create index if not exists idx_kb_change_outbox_tenant
    on kb_change_outbox (tenant_id, id);

commit;
