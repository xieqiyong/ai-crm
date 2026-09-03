package com.hz.crm.domain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.domain.task.SalesTaskEntity;
import com.hz.crm.domain.task.TaskCompletionRankingProjection;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesTaskMapper extends BaseMapper<SalesTaskEntity> {

    @Select({"<script>",
            "select cast(u.id as varchar) as user_id, ",
            "coalesce(nullif(trim(u.display_name), ''), u.username) as user_name, ",
            "count(t.id) as completed_task_count, max(t.completed_at) as last_completed_at ",
            "from sys_user u ",
            "left join crm_sales_task t on t.tenant_id = u.tenant_id ",
            "and t.owner_id = u.id and t.deleted = false and t.status = 'COMPLETED' ",
            "and t.completed_at &gt;= #{todayStart} and t.completed_at &lt; #{tomorrowStart} ",
            "where u.tenant_id = #{tenantId} and u.deleted = false and u.enabled = true ",
            "and lower(u.username) &lt;&gt; 'admin' ",
            "<if test='scopeRestricted'>",
            "<choose>",
            "<when test='ownerIds != null and ownerIds.size() > 0'>",
            "and u.id in ",
            "<foreach collection='ownerIds' item='ownerId' open='(' separator=',' close=')'>#{ownerId}</foreach>",
            "</when>",
            "<otherwise>and 1 = 0</otherwise>",
            "</choose>",
            "</if>",
            "group by u.id, u.username, u.display_name, u.created_at ",
            "order by count(t.id) desc, max(t.completed_at) desc nulls last, u.created_at asc ",
            "limit #{limit}",
            "</script>"})
    List<TaskCompletionRankingProjection> selectTodayCompletionRanking(
            @Param("tenantId") Long tenantId,
            @Param("scopeRestricted") boolean scopeRestricted,
            @Param("ownerIds") List<Long> ownerIds,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("tomorrowStart") LocalDateTime tomorrowStart,
            @Param("limit") int limit);
}
