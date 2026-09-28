create table if not exists schema_migration (
    version text primary key,
    applied_at text not null
);

create table if not exists sync_run (
    id integer primary key autoincrement,
    source_id text not null,
    trigger_type text not null,
    status text not null,
    file_path text,
    file_hash text,
    total_count integer not null default 0,
    created_count integer not null default 0,
    changed_count integer not null default 0,
    skipped_count integer not null default 0,
    invalid_count integer not null default 0,
    error_message text,
    started_at text not null,
    finished_at text
);

create table if not exists source_row (
    id integer primary key autoincrement,
    source_id text not null,
    row_key text not null,
    content_hash text not null,
    row_number integer,
    raw_json text not null,
    status text not null,
    validation_error text,
    first_seen_at text not null,
    last_seen_at text not null,
    updated_at text not null,
    unique(source_id, row_key)
);

create table if not exists license_outbox (
    id integer primary key autoincrement,
    source_row_id integer not null,
    idempotency_key text not null,
    request_json text not null,
    status text not null,
    attempt_count integer not null default 0,
    next_retry_at text,
    last_error text,
    created_at text not null,
    updated_at text not null,
    unique(source_row_id),
    unique(idempotency_key),
    foreign key(source_row_id) references source_row(id)
);

create table if not exists license_application (
    id integer primary key autoincrement,
    outbox_id integer not null,
    source_row_id integer not null,
    application_no text,
    response_json text not null,
    created_at text not null,
    unique(outbox_id),
    foreign key(outbox_id) references license_outbox(id),
    foreign key(source_row_id) references source_row(id)
);

create index if not exists idx_sync_run_source_started
    on sync_run(source_id, started_at desc);

create index if not exists idx_source_row_status
    on source_row(source_id, status, updated_at);

create index if not exists idx_license_outbox_status_retry
    on license_outbox(status, next_retry_at, updated_at);
