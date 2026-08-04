package com.hz.crm.mcp.tool;

import com.hz.crm.application.customer.CustomerApplicationService;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.followup.dto.FollowupResponse;
import com.hz.crm.application.opportunity.OpportunityApplicationService;
import com.hz.crm.application.opportunity.dto.OpportunityProductResponse;
import com.hz.crm.application.opportunity.dto.OpportunityQuery;
import com.hz.crm.application.opportunity.dto.OpportunityResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.domain.opportunity.OpportunityStage;
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
public class CrmOpportunityMcpTool extends CrmMcpToolSupport {

    @Autowired
    private OpportunityApplicationService opportunityApplicationService;

    @Autowired
    private CustomerApplicationService customerApplicationService;

    @Autowired
    private CrmCustomerMcpTool customerMcpTool;

    @Autowired
    private CrmFollowupMcpTool followupMcpTool;

    @McpTool(name = "crm_opportunity_page", description = "分页查询商机列表，支持按关键词、阶段和客户过滤，返回真实商机数据")
    public Map<String, Object> opportunityPage(
            @McpToolParam(description = "租户编号字符串，独立MCP服务必传", required = true) String tenantId,
            @McpToolParam(description = "用户编号字符串，独立MCP服务必传", required = true) String userId,
            @McpToolParam(description = "数据权限范围，可选ALL、SELF；默认SELF", required = false) String dataScope,
            @McpToolParam(description = "商机关键词，可匹配商机名称和备注", required = false) String keyword,
            @McpToolParam(description = "商机阶段枚举值", required = false) String stage,
            @McpToolParam(description = "客户编号字符串", required = false) String customerId,
            @McpToolParam(description = "页码，默认1", required = false) Integer pageNo,
            @McpToolParam(description = "每页数量，默认10，最大50", required = false) Integer pageSize) {
        CrmMcpContext context = resolveContext(tenantId, userId, dataScope);
        OpportunityQuery query = new OpportunityQuery();
        query.setKeyword(trimToNull(keyword));
        query.setStage(optionalEnum(stage, OpportunityStage.class, "商机阶段"));
        query.setCustomerId(optionalId(customerId, "客户编号"));
        query.setPageNo(pageNo(pageNo));
        query.setPageSize(pageSize(pageSize));
        PageData<OpportunityResponse> page = opportunityApplicationService.page(
                context.getTenantId(), context.getUserId(), context.getDataScope(), query);
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        for (OpportunityResponse response : page.getRecords()) {
            records.add(toOpportunityMap(response));
        }
        return pageResult(page, records);
    }

    @McpTool(name = "crm_opportunity_detail", description = "查询商机详情，返回商机基础信息、客户、金额、阶段、产品明细和负责人")
    public Map<String, Object> opportunityDetail(
            @McpToolParam(description = "商机编号字符串", required = true) String opportunityId,
            @McpToolParam(description = "租户编号字符串，独立MCP服务必传", required = true) String tenantId,
            @McpToolParam(description = "用户编号字符串，独立MCP服务必传", required = true) String userId,
            @McpToolParam(description = "数据权限范围，可选ALL、SELF；默认SELF", required = false) String dataScope) {
        CrmMcpContext context = resolveContext(tenantId, userId, dataScope);
        OpportunityResponse response = opportunityApplicationService.detail(
                context.getTenantId(), context.getUserId(), context.getDataScope(), requiredId(opportunityId, "商机编号"));
        return detailResult(toOpportunityMap(response));
    }

    @McpTool(name = "crm_opportunity_customer_followup_overview", description = "按商机编号读取商机、关联客户详情、商机跟进记录和客户跟进记录，用于形成销售全景上下文")
    public Map<String, Object> opportunityCustomerFollowupOverview(
            @McpToolParam(description = "商机编号字符串", required = true) String opportunityId,
            @McpToolParam(description = "租户编号字符串，独立MCP服务必传", required = true) String tenantId,
            @McpToolParam(description = "用户编号字符串，独立MCP服务必传", required = true) String userId,
            @McpToolParam(description = "数据权限范围，可选ALL、SELF；默认SELF", required = false) String dataScope,
            @McpToolParam(description = "每类跟进记录返回数量，默认10，最大50", required = false) Integer followupLimit) {
        CrmMcpContext context = resolveContext(tenantId, userId, dataScope);
        OpportunityResponse opportunity = opportunityApplicationService.detail(
                context.getTenantId(), context.getUserId(), context.getDataScope(), requiredId(opportunityId, "商机编号"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("opportunity", toOpportunityMap(opportunity));
        if (opportunity.getCustomerId() != null) {
            CustomerResponse customer = customerApplicationService.detail(
                    context.getTenantId(), context.getUserId(), context.getDataScope(), opportunity.getCustomerId());
            data.put("customer", customerMcpTool.toCustomerMap(customer));
            PageData<FollowupResponse> customerFollowups = followupMcpTool.queryFollowups(
                    context, null, "CUSTOMER", opportunity.getCustomerId(), null, 1, pageSize(followupLimit));
            data.put("customerFollowups", followupMcpTool.toFollowupMaps(customerFollowups.getRecords()));
        } else {
            data.put("customer", null);
            data.put("customerFollowups", new ArrayList<Map<String, Object>>());
        }
        PageData<FollowupResponse> opportunityFollowups = followupMcpTool.queryFollowups(
                context, null, "OPPORTUNITY", opportunity.getId(), null, 1, pageSize(followupLimit));
        data.put("opportunityFollowups", followupMcpTool.toFollowupMaps(opportunityFollowups.getRecords()));
        return overviewResult(data);
    }

    private Map<String, Object> toOpportunityMap(OpportunityResponse response) {
        Map<String, Object> record = new LinkedHashMap<String, Object>();
        if (response == null) {
            return record;
        }
        record.put("id", id(response.getId()));
        record.put("tenantId", id(response.getTenantId()));
        record.put("name", response.getName());
        record.put("customerId", id(response.getCustomerId()));
        record.put("customerName", response.getCustomerName());
        record.put("amount", response.getAmount());
        record.put("stage", enumName(response.getStage()));
        record.put("probability", response.getProbability());
        record.put("expectedCloseDate", date(response.getExpectedCloseDate()));
        record.put("ownerId", id(response.getOwnerId()));
        record.put("ownerName", response.getOwnerName());
        record.put("products", toProductMaps(response.getProducts()));
        record.put("remark", shrink(response.getRemark(), 1500));
        record.put("createdAt", dateTime(response.getCreatedAt()));
        record.put("updatedAt", dateTime(response.getUpdatedAt()));
        return record;
    }

    private List<Map<String, Object>> toProductMaps(List<OpportunityProductResponse> products) {
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        if (products == null) {
            return records;
        }
        for (OpportunityProductResponse product : products) {
            Map<String, Object> record = new LinkedHashMap<String, Object>();
            record.put("id", id(product.getId()));
            record.put("productId", id(product.getProductId()));
            record.put("productCode", product.getProductCode());
            record.put("productName", product.getProductName());
            record.put("category", product.getCategory());
            record.put("productType", product.getProductType());
            record.put("quantity", product.getQuantity());
            record.put("unitPrice", product.getUnitPrice());
            record.put("discountRate", product.getDiscountRate());
            record.put("subtotal", product.getSubtotal());
            record.put("unit", product.getUnit());
            record.put("remark", shrink(product.getRemark(), 1000));
            records.add(record);
        }
        return records;
    }
}
