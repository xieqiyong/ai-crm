-- 本脚本只补齐空值，不覆盖任何已经存在的意向产品。
-- 执行前请完成数据库备份，并先确认渠道来源已经正确关联产品。

begin;

alter table crm_customer
    add column if not exists product_id bigint;

-- 按渠道来源的稳定外部标识补齐渠道原始记录产品。
with record_product_candidates as (
    select
        channel_record.id as record_id,
        min(channel_source.product_id) as product_id
    from crm_channel_record channel_record
    join crm_channel_source channel_source
      on channel_source.tenant_id = channel_record.tenant_id
     and channel_source.product_id is not null
     and (
          (
              channel_record.external_provider = 'WECOM_SMART_SHEET'
              and channel_record.external_key like channel_source.external_key || ':%'
          )
          or
          (
              channel_record.external_provider = 'WECOM_SMART_SHEET_EXPORT'
              and channel_record.external_key like channel_source.id::varchar || ':%'
          )
     )
    where channel_record.product_id is null
    group by channel_record.id
    having count(distinct channel_source.product_id) = 1
)
update crm_channel_record channel_record
set product_id = candidate.product_id,
    updated_at = current_timestamp
from record_product_candidates candidate
where channel_record.id = candidate.record_id
  and channel_record.product_id is null;

-- 通过渠道记录中已经保存的线索编号补齐线索产品。
with lead_product_candidates as (
    select
        channel_record.lead_id,
        min(channel_record.product_id) as product_id
    from crm_channel_record channel_record
    where channel_record.lead_id is not null
      and channel_record.product_id is not null
    group by channel_record.lead_id
    having count(distinct channel_record.product_id) = 1
)
update crm_lead lead
set product_id = candidate.product_id,
    updated_at = current_timestamp
from lead_product_candidates candidate
where lead.id = candidate.lead_id
  and lead.product_id is null;

-- 通过已转化线索与客户的稳定编号关系补齐客户产品。
with customer_product_candidates as (
    select
        lead.customer_id,
        min(lead.product_id) as product_id
    from crm_lead lead
    where lead.customer_id is not null
      and lead.product_id is not null
    group by lead.customer_id
    having count(distinct lead.product_id) = 1
)
update crm_customer customer
set product_id = candidate.product_id,
    updated_at = current_timestamp
from customer_product_candidates candidate
where customer.id = candidate.customer_id
  and customer.product_id is null;

commit;

-- 以下结果必须由开发人员逐项确认，本脚本不会使用公司名称或手机号做模糊关联。
select id, tenant_id, deleted, name, product_id
from crm_channel_source
where product_id is null
order by tenant_id, id;

select id, tenant_id, deleted, company_name, name, product_id
from crm_lead
where product_id is null
order by tenant_id, id;

select id, tenant_id, deleted, name, product_id
from crm_customer
where product_id is null
order by tenant_id, id;

select
    lead.id as lead_id,
    lead.company_name,
    lead.product_id as lead_product_id,
    customer.id as customer_id,
    customer.name as customer_name,
    customer.product_id as customer_product_id
from crm_lead lead
join crm_customer customer
  on customer.id = lead.customer_id
 and customer.tenant_id = lead.tenant_id
 and customer.deleted = false
where lead.deleted = false
  and lead.product_id is not null
  and customer.product_id is not null
  and lead.product_id <> customer.product_id
order by lead.tenant_id, customer.id, lead.id;
