package com.hz.crm.domain.lead.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadFollowupProjection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LeadMapper extends BaseMapper<LeadEntity> {

    @Select({"<script>",
            "select l.id as lead_id, l.name as lead_name, l.company_name, l.phone, l.owner_id, ",
            "l.created_at, p.name as product_name, ",
            "coalesce(nullif(trim(u.display_name), ''), u.username) as owner_name, ",
            "f.followup_at as last_followup_at, f.next_follow_time ",
            "from crm_lead l ",
            "left join crm_product p on p.id = l.product_id and p.tenant_id = l.tenant_id and p.deleted = false ",
            "left join sys_user u on u.id = l.owner_id and u.tenant_id = l.tenant_id and u.deleted = false ",
            "left join lateral (",
            "select r.followup_at, r.next_follow_time ",
            "from crm_followup_record r ",
            "where r.tenant_id = l.tenant_id and r.deleted = false ",
            "and r.target_type = 'LEAD' and r.target_id = l.id ",
            "order by r.followup_at desc nulls last, r.created_at desc, r.id desc limit 1",
            ") f on true ",
            "where l.tenant_id = #{tenantId} and l.deleted = false ",
            "and l.status not in ('CONVERTED', 'INVALID', 'DUPLICATE', 'CLOSED') ",
            "<if test='scopeRestricted'>",
            "<choose>",
            "<when test='ownerIds != null and ownerIds.size() > 0'>",
            "and l.owner_id in ",
            "<foreach collection='ownerIds' item='ownerId' open='(' separator=',' close=')'>#{ownerId}</foreach>",
            "</when>",
            "<otherwise>and 1 = 0</otherwise>",
            "</choose>",
            "</if>",
            "order by l.created_at desc, l.id desc",
            "</script>"})
    List<LeadFollowupProjection> selectFollowupLeads(
            @Param("tenantId") Long tenantId,
            @Param("scopeRestricted") boolean scopeRestricted,
            @Param("ownerIds") List<Long> ownerIds);
}
