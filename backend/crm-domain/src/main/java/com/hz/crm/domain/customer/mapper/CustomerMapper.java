package com.hz.crm.domain.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.domain.customer.CustomerFollowupProjection;
import com.hz.crm.domain.customer.CustomerEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerMapper extends BaseMapper<CustomerEntity> {

    @Select({"<script>",
            "select c.id as customer_id, c.name as customer_name, ",
            "c.contact_name, c.contact_phone, c.owner_id, ",
            "c.created_at, p.name as product_name, ",
            "coalesce(nullif(trim(u.display_name), ''), u.username) as owner_name, ",
            "f.followup_at as last_followup_at, f.next_follow_time ",
            "from crm_customer c ",
            "left join crm_product p on p.id = c.product_id and p.tenant_id = c.tenant_id and p.deleted = false ",
            "left join sys_user u on u.id = c.owner_id and u.tenant_id = c.tenant_id and u.deleted = false ",
            "left join lateral (",
            "select r.followup_at, r.next_follow_time ",
            "from crm_followup_record r ",
            "where r.tenant_id = c.tenant_id and r.deleted = false ",
            "and r.target_type = 'CUSTOMER' and r.target_id = c.id ",
            "order by r.followup_at desc nulls last, r.created_at desc, r.id desc limit 1",
            ") f on true ",
            "where c.tenant_id = #{tenantId} and c.deleted = false ",
            "and c.status not in ('COOPERATED', 'CHURNED', 'BLACKLIST') ",
            "<if test='scopeRestricted'>",
            "<choose>",
            "<when test='ownerIds != null and ownerIds.size() > 0'>",
            "and c.owner_id in ",
            "<foreach collection='ownerIds' item='ownerId' open='(' separator=',' close=')'>#{ownerId}</foreach>",
            "</when>",
            "<otherwise>and 1 = 0</otherwise>",
            "</choose>",
            "</if>",
            "order by c.created_at desc, c.id desc",
            "</script>"})
    List<CustomerFollowupProjection> selectFollowupCustomers(
            @Param("tenantId") Long tenantId,
            @Param("scopeRestricted") boolean scopeRestricted,
            @Param("ownerIds") List<Long> ownerIds);
}
