package com.hz.crm.mcp.tool;

import com.hz.crm.application.customer.CustomerApplicationService;
import com.hz.crm.application.customer.dto.CustomerQuery;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.domain.customer.CustomerStatus;
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
public class CrmCustomerMcpTool extends CrmMcpToolSupport {

    @Autowired
    private CustomerApplicationService customerApplicationService;

    @McpTool(name = "crm_customer_page", description = "分页查询客户列表，支持按关键词和客户状态过滤，返回真实客户数据")
    public Map<String, Object> customerPage(
            @McpToolParam(description = "租户编号字符串，独立MCP服务必传", required = true) String tenantId,
            @McpToolParam(description = "用户编号字符串，独立MCP服务必传", required = true) String userId,
            @McpToolParam(description = "数据权限范围，可选ALL、SELF；默认SELF", required = false) String dataScope,
            @McpToolParam(description = "客户关键词，可匹配名称、行业、联系人、电话、邮箱", required = false) String keyword,
            @McpToolParam(description = "客户状态枚举值", required = false) String status,
            @McpToolParam(description = "页码，默认1", required = false) Integer pageNo,
            @McpToolParam(description = "每页数量，默认10，最大50", required = false) Integer pageSize) {
        CrmMcpContext context = resolveContext(tenantId, userId, dataScope);
        CustomerQuery query = new CustomerQuery();
        query.setKeyword(trimToNull(keyword));
        query.setStatus(optionalEnum(status, CustomerStatus.class, "客户状态"));
        query.setPageNo(pageNo(pageNo));
        query.setPageSize(pageSize(pageSize));
        PageData<CustomerResponse> page = customerApplicationService.page(
                context.getTenantId(), context.getUserId(), context.getDataScope(), query);
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        for (CustomerResponse response : page.getRecords()) {
            records.add(toCustomerMap(response));
        }
        return pageResult(page, records);
    }

    @McpTool(name = "crm_customer_detail", description = "查询客户详情，返回客户基础信息、状态、负责人和AI总结")
    public Map<String, Object> customerDetail(
            @McpToolParam(description = "客户编号字符串", required = true) String customerId,
            @McpToolParam(description = "租户编号字符串，独立MCP服务必传", required = true) String tenantId,
            @McpToolParam(description = "用户编号字符串，独立MCP服务必传", required = true) String userId,
            @McpToolParam(description = "数据权限范围，可选ALL、SELF；默认SELF", required = false) String dataScope) {
        CrmMcpContext context = resolveContext(tenantId, userId, dataScope);
        CustomerResponse response = customerApplicationService.detail(
                context.getTenantId(), context.getUserId(), context.getDataScope(), requiredId(customerId, "客户编号"));
        return detailResult(toCustomerMap(response));
    }

    public Map<String, Object> toCustomerMap(CustomerResponse response) {
        Map<String, Object> record = new LinkedHashMap<String, Object>();
        if (response == null) {
            return record;
        }
        record.put("id", id(response.getId()));
        record.put("tenantId", id(response.getTenantId()));
        record.put("name", response.getName());
        record.put("industry", response.getIndustry());
        record.put("contactName", response.getContactName());
        record.put("contactPhone", response.getContactPhone());
        record.put("contactEmail", response.getContactEmail());
        record.put("level", enumName(response.getLevel()));
        record.put("status", enumName(response.getStatus()));
        record.put("ownerId", id(response.getOwnerId()));
        record.put("ownerName", response.getOwnerName());
        record.put("productId", id(response.getProductId()));
        record.put("productName", response.getProductName());
        record.put("remark", shrink(response.getRemark(), 1500));
        record.put("aiSummary", shrink(response.getAiSummary(), 3000));
        record.put("aiAnalyzedAt", dateTime(response.getAiAnalyzedAt()));
        record.put("createdAt", dateTime(response.getCreatedAt()));
        record.put("updatedAt", dateTime(response.getUpdatedAt()));
        return record;
    }
}
