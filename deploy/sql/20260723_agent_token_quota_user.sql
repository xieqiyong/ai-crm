create table if not exists agent_token_quota_user (
    id bigint primary key,
    tenant_id bigint not null,
    user_id bigint not null,
    daily_token_limit bigint not null default 0,
    assign_scope varchar(32) not null default 'USER',
    assign_target_id bigint,
    assign_target_name varchar(128),
    remark varchar(512),
    enabled boolean not null default true,
    deleted boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists uk_agent_token_quota_user
    on agent_token_quota_user (tenant_id, user_id);

create index if not exists idx_agent_token_quota_user_tenant
    on agent_token_quota_user (tenant_id);

create index if not exists idx_agent_token_quota_user_user
    on agent_token_quota_user (tenant_id, user_id);
