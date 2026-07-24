package com.hz.crm.agent.runtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.agent.runtime.domain.AgentTokenQuotaUserEntity;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaDepartmentOption;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaUserOption;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentTokenQuotaUserMapper extends BaseMapper<AgentTokenQuotaUserEntity> {

    @Select("select u.id, u.username, u.display_name, u.department_id, d.name as department_name, u.enabled "
            + "from sys_user u "
            + "left join sys_department d on d.id = u.department_id "
            + "and d.tenant_id = u.tenant_id and d.deleted = false "
            + "where u.tenant_id = #{tenantId} and u.deleted = false "
            + "order by u.created_at desc")
    List<AgentTokenQuotaUserOption> users(@Param("tenantId") Long tenantId);

    @Select("select id, parent_id, name, enabled "
            + "from sys_department "
            + "where tenant_id = #{tenantId} and deleted = false "
            + "order by sort_no asc, created_at asc")
    List<AgentTokenQuotaDepartmentOption> departments(@Param("tenantId") Long tenantId);
}
