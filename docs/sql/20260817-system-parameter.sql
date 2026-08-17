create table if not exists crm_system_parameter (
    id bigint primary key,
    tenant_id bigint not null,
    deleted boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp not null,
    param_key varchar(128) not null,
    param_value text not null,
    name varchar(128) not null,
    description varchar(512),
    group_code varchar(64) not null,
    value_type varchar(32) not null,
    sort_no integer not null default 0,
    constraint uk_system_parameter_key unique (tenant_id, param_key)
);

create index if not exists idx_system_parameter_group
    on crm_system_parameter (tenant_id, group_code);
