package com.hz.crm.mcp.tool;

import com.hz.crm.application.followup.FollowupApplicationService;
import com.hz.crm.application.followup.dto.FollowupQuery;
import com.hz.crm.application.followup.dto.FollowupResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.followup.FollowupType;
import com.hz.crm.mcp.support.CrmMcpContext;
import com.hz.crm.mcp.support.CrmMcpToolSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CrmFollowupMcpTool extends CrmMcpToolSupport {

    @Autowired
    private FollowupApplicationService followupApplicationService;

    @McpTool(name = "crm_followup_page", description = "分页查询跟进记录，支持按跟进对象、跟进类型和关键词过滤")
    public Map<String, Object> followupPage(
            @McpToolParam(description = "租户编号字符串，独立MCP服务必传", required = true) String tenantId,
            @McpToolParam(description = "用户编号字符串，独立MCP服务必传", required = true) String userId,
            @McpToolParam(description = "数据权限范围，可选ALL、SELF；默认SELF", required = false) String dataScope,
            @McpToolParam(description = "关键词，可匹配跟进对象、内容、结果、下一步计划", required = false) String keyword,
            @McpToolParam(description = "跟进对象类型，可选LEAD、CUSTOMER、OPPORTUNITY", required = false) String targetType,
            @McpToolParam(description = "跟进对象编号字符串", required = false) String targetId,
            @McpToolParam(description = "跟进类型枚举值", required = false) String followupType,
            @McpToolParam(description = "页码，默认1", required = false) Integer pageNo,
            @McpToolParam(description = "每页数量，默认10，最大50", required = false) Integer pageSize) {
        CrmMcpContext context = resolveContext(tenantId, userId, dataScope);
        FollowupQuery query = buildQuery(keyword, targetType, targetId, followupType, pageNo, pageSize);
        PageData<FollowupResponse> page = followupApplicationService.page(
                context.getTenantId(), context.getUserId(), context.getDataScope(), query);
        return pageResult(page, toFollowupMaps(page.getRecords()));
    }

    @McpTool(name = "crm_followup_detail", description = "查询单条跟进记录详情，返回跟进对象、内容、结果和下一步计划")
    public Map<String, Object> followupDetail(
            @McpToolParam(description = "跟进记录编号字符串", required = true) String followupId,
            @McpToolParam(description = "租户编号字符串，独立MCP服务必传", required = true) String tenantId,
            @McpToolParam(description = "用户编号字符串，独立MCP服务必传", required = true) String userId,
            @McpToolParam(description = "数据权限范围，可选ALL、SELF；默认SELF", required = false) String dataScope) {
        CrmMcpContext context = resolveContext(tenantId, userId, dataScope);
        FollowupResponse response = followupApplicationService.detail(
                context.getTenantId(), context.getUserId(), context.getDataScope(), requiredId(followupId, "跟进记录编号"));
        return detailResult(toFollowupMap(response));
    }

    public PageData<FollowupResponse> queryFollowups(
            CrmMcpContext context,
            String keyword,
            String targetType,
            Long targetId,
            String followupType,
            Integer pageNo,
            Integer pageSize) {
        FollowupQuery query = buildQuery(keyword, targetType, id(targetId), followupType, pageNo, pageSize);
        return followupApplicationService.page(
                context.getTenantId(), context.getUserId(), context.getDataScope(), query);
    }

    public List<Map<String, Object>> toFollowupMaps(List<FollowupResponse> responses) {
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        if (responses == null) {
            return records;
        }
        for (FollowupResponse response : responses) {
            records.add(toFollowupMap(response));
        }
        return records;
    }

    public Map<String, Object> toFollowupMap(FollowupResponse response) {
        Map<String, Object> record = new LinkedHashMap<String, Object>();
        if (response == null) {
            return record;
        }
        record.put("id", id(response.getId()));
        record.put("tenantId", id(response.getTenantId()));
        record.put("targetType", enumName(response.getTargetType()));
        record.put("targetId", id(response.getTargetId()));
        record.put("targetName", response.getTargetName());
        record.put("followupType", enumName(response.getFollowupType()));
        record.put("followupAt", dateTime(response.getFollowupAt()));
        record.put("contentText", plainText(response.getContent(), 2500));
        record.put("contentHtml", shrink(response.getContent(), 4000));
        record.put("result", shrink(response.getResult(), 1500));
        record.put("nextPlan", shrink(response.getNextPlan(), 1500));
        record.put("nextFollowTime", dateTime(response.getNextFollowTime()));
        record.put("ownerId", id(response.getOwnerId()));
        record.put("ownerName", response.getOwnerName());
        record.put("createdAt", dateTime(response.getCreatedAt()));
        record.put("updatedAt", dateTime(response.getUpdatedAt()));
        return record;
    }

    private FollowupQuery buildQuery(
            String keyword,
            String targetType,
            String targetId,
            String followupType,
            Integer pageNo,
            Integer pageSize) {
        FollowupQuery query = new FollowupQuery();
        query.setKeyword(trimToNull(keyword));
        query.setTargetType(optionalEnum(targetType, FollowupTargetType.class, "跟进对象类型"));
        query.setTargetId(optionalId(targetId, "跟进对象编号"));
        query.setFollowupType(optionalEnum(followupType, FollowupType.class, "跟进类型"));
        query.setPageNo(pageNo(pageNo));
        query.setPageSize(pageSize(pageSize));
        return query;
    }
}
