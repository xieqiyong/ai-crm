package com.hz.crm.domain.followup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.domain.followup.FollowupObjectProjection;
import com.hz.crm.domain.followup.FollowupRankingProjection;
import com.hz.crm.domain.followup.FollowupRecordEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FollowupRecordMapper extends BaseMapper<FollowupRecordEntity> {

    @Select("select cast(u.id as varchar) as user_id, "
            + "coalesce(nullif(trim(u.display_name), ''), u.username) as user_name, "
            + "count(f.id) as followup_count, "
            + "max(f.followup_at) as last_followup_at "
            + "from sys_user u "
            + "left join crm_followup_record f on f.tenant_id = u.tenant_id "
            + "and f.owner_id = u.id "
            + "and f.deleted = false "
            + "and f.followup_at >= #{todayStart} "
            + "and f.followup_at < #{tomorrowStart} "
            + "where u.tenant_id = #{tenantId} "
            + "and u.deleted = false "
            + "and u.enabled = true "
            + "and lower(u.username) <> 'admin' "
            + "group by u.id, u.username, u.display_name, u.created_at "
            + "order by count(f.id) desc, max(f.followup_at) desc nulls last, u.created_at asc "
            + "limit #{limit}")
    List<FollowupRankingProjection> selectTodayFollowupRanking(
            @Param("tenantId") Long tenantId,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("tomorrowStart") LocalDateTime tomorrowStart,
            @Param("limit") int limit);

    @Select({"<script>",
            "with filtered as (",
            "select f.* from crm_followup_record f ",
            "where f.tenant_id = #{tenantId} ",
            "and f.deleted = false ",
            "<if test='selfScope'>and f.owner_id = #{userId} </if>",
            "<if test='targetType != null and targetType != \"\"'>and f.target_type = #{targetType} </if>",
            "<if test='targetId != null'>and f.target_id = #{targetId} </if>",
            "<if test='followupType != null and followupType != \"\"'>and f.followup_type = #{followupType} </if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "and (lower(coalesce(f.target_name, '')) like #{keyword} ",
            "or lower(coalesce(f.content, '')) like #{keyword} ",
            "or lower(coalesce(f.result, '')) like #{keyword} ",
            "or lower(coalesce(f.next_plan, '')) like #{keyword}) ",
            "</if>",
            ") ",
            "select count(*) from (",
            "select target_type, target_id from filtered group by target_type, target_id",
            ") grouped",
            "</script>"})
    Long countFollowupObjects(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("selfScope") boolean selfScope,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("followupType") String followupType,
            @Param("keyword") String keyword);

    @Select({"<script>",
            "with filtered as (",
            "select f.* from crm_followup_record f ",
            "where f.tenant_id = #{tenantId} ",
            "and f.deleted = false ",
            "<if test='selfScope'>and f.owner_id = #{userId} </if>",
            "<if test='targetType != null and targetType != \"\"'>and f.target_type = #{targetType} </if>",
            "<if test='targetId != null'>and f.target_id = #{targetId} </if>",
            "<if test='followupType != null and followupType != \"\"'>and f.followup_type = #{followupType} </if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "and (lower(coalesce(f.target_name, '')) like #{keyword} ",
            "or lower(coalesce(f.content, '')) like #{keyword} ",
            "or lower(coalesce(f.result, '')) like #{keyword} ",
            "or lower(coalesce(f.next_plan, '')) like #{keyword}) ",
            "</if>",
            "), grouped as (",
            "select target_type, target_id, count(id) as followup_count, ",
            "max(followup_at) as latest_followup_at, max(created_at) as latest_created_at ",
            "from filtered group by target_type, target_id",
            ") ",
            "select f.id, f.tenant_id, f.target_type, f.target_id, f.target_name, ",
            "f.followup_type, f.followup_at, f.content, f.result, f.next_plan, ",
            "f.next_follow_time, f.owner_id, f.created_at, f.updated_at, ",
            "g.followup_count ",
            "from grouped g ",
            "join lateral (",
            "select latest.* from filtered latest ",
            "where latest.target_type = g.target_type and latest.target_id = g.target_id ",
            "order by latest.followup_at desc nulls last, latest.created_at desc, latest.id desc ",
            "limit 1",
            ") f on true ",
            "order by g.latest_followup_at desc nulls last, g.latest_created_at desc nulls last ",
            "limit #{pageSize} offset #{offset}",
            "</script>"})
    List<FollowupObjectProjection> selectFollowupObjectPage(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("selfScope") boolean selfScope,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("followupType") String followupType,
            @Param("keyword") String keyword,
            @Param("pageSize") int pageSize,
            @Param("offset") long offset);
}
