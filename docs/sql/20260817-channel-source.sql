-- 渠道来源配置表
create table if not exists crm_channel_source (
    id bigint not null primary key,
    tenant_id bigint not null,
    deleted boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    name varchar(128) not null,
    source_type varchar(32) not null,
    status varchar(32) not null,
    sync_mode varchar(32) not null,
    source_url text,
    external_provider varchar(32),
    external_key varchar(512),
    wecom_config_id bigint,
    product_id bigint,
    doc_id varchar(128),
    sheet_id varchar(64),
    view_id varchar(64),
    field_mapping_json text,
    sync_interval_minutes integer not null,
    auto_sync boolean not null,
    auto_analyze boolean not null,
    owner_id bigint,
    last_sync_at timestamp,
    last_success_at timestamp,
    last_error text,
    total_record_count bigint,
    today_new_count bigint,
    converted_lead_count bigint,
    duplicate_count bigint,
    failed_count bigint,
    latest_field_snapshot text
);

create unique index if not exists uk_channel_source_external
    on crm_channel_source (tenant_id, external_provider, external_key);

create index if not exists idx_channel_source_tenant_status
    on crm_channel_source (tenant_id, status, source_type);

-- 渠道来源同步日志表
create table if not exists crm_channel_sync_log (
    id bigint not null primary key,
    tenant_id bigint not null,
    deleted boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    source_id bigint not null,
    trigger_type varchar(32) not null,
    status varchar(32) not null,
    started_at timestamp,
    finished_at timestamp,
    fetched_count integer,
    created_count integer,
    updated_count integer,
    skipped_count integer,
    failed_count integer,
    error_message text
);

create index if not exists idx_channel_sync_log_source
    on crm_channel_sync_log (tenant_id, source_id, created_at desc);
