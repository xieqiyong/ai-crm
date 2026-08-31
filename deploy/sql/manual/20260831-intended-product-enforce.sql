-- 只有在回填脚本的待处理结果全部清零后才能执行本脚本。
-- 任一校验不通过都会直接停止，应用不会自动修复历史数据。

begin;

do $$
begin
    if exists (
        select 1 from crm_channel_source
        where product_id is null
    ) then
        raise exception '仍有渠道来源未关联产品，请先手工处理';
    end if;

    if exists (
        select 1 from crm_lead
        where product_id is null
    ) then
        raise exception '仍有线索未关联意向产品，请先手工处理';
    end if;

    if exists (
        select 1 from crm_customer
        where product_id is null
    ) then
        raise exception '仍有客户未关联意向产品，请先手工处理';
    end if;

    if exists (
        select 1
        from crm_lead lead
        left join crm_product product
          on product.id = lead.product_id
         and product.tenant_id = lead.tenant_id
         and product.deleted = false
        where lead.deleted = false
          and product.id is null
    ) then
        raise exception '存在线索关联了无效产品或其他租户产品，请先手工处理';
    end if;

    if exists (
        select 1
        from crm_customer customer
        left join crm_product product
          on product.id = customer.product_id
         and product.tenant_id = customer.tenant_id
         and product.deleted = false
        where customer.deleted = false
          and product.id is null
    ) then
        raise exception '存在客户关联了无效产品或其他租户产品，请先手工处理';
    end if;

    if exists (
        select 1
        from crm_lead lead
        join crm_customer customer
          on customer.id = lead.customer_id
         and customer.tenant_id = lead.tenant_id
         and customer.deleted = false
        where lead.deleted = false
          and lead.product_id <> customer.product_id
    ) then
        raise exception '存在已转化线索与客户的意向产品冲突，请先明确主产品并手工处理';
    end if;
end
$$;

alter table crm_channel_source
    alter column product_id set not null;

alter table crm_lead
    alter column product_id set not null;

alter table crm_customer
    alter column product_id set not null;

create index if not exists idx_crm_lead_tenant_product
    on crm_lead (tenant_id, product_id)
    where deleted = false;

create index if not exists idx_crm_customer_tenant_product
    on crm_customer (tenant_id, product_id)
    where deleted = false;

commit;
