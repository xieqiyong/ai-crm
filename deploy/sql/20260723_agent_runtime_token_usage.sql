alter table if exists agent_run add column if not exists input_token_count bigint;
alter table if exists agent_run add column if not exists output_token_count bigint;
alter table if exists agent_run add column if not exists total_token_count bigint;
alter table if exists agent_run add column if not exists estimated_token_count bigint;
alter table if exists agent_run add column if not exists usage_estimated boolean default true;
alter table if exists agent_run add column if not exists reserved_token_count bigint;
alter table if exists agent_run add column if not exists daily_token_limit bigint;

do $$
begin
    if exists (
        select 1
        from information_schema.tables
        where table_schema = current_schema()
          and table_name = 'agent_run'
    ) then
        update agent_run set usage_estimated = true where usage_estimated is null;
    end if;
end $$;

create table if not exists agent_token_usage (
    id bigint primary key,
    tenant_id bigint not null,
    user_id bigint not null,
    usage_date date not null,
    input_token_count bigint not null default 0,
    output_token_count bigint not null default 0,
    total_token_count bigint not null default 0,
    estimated_token_count bigint not null default 0,
    reserved_token_count bigint not null default 0,
    request_count bigint not null default 0,
    success_count bigint not null default 0,
    failed_count bigint not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists uk_agent_token_usage_user_day
    on agent_token_usage (tenant_id, user_id, usage_date);

create index if not exists idx_agent_token_usage_tenant_day
    on agent_token_usage (tenant_id, usage_date);

create index if not exists idx_agent_token_usage_user_day
    on agent_token_usage (tenant_id, user_id, usage_date);
