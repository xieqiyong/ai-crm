package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.application.customer.CustomerApplicationService;
import com.hz.crm.application.customer.dto.CustomerQuery;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.customer.CustomerStatus;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CustomerQueryTool extends CrmQueryToolSupport implements AgentTool {

    public static final String TOOL_NAME = "crm_query_customers";

    @Autowired
    private CustomerApplicationService customerApplicationService;

    public CustomerQueryTool bind(AgentRuntimeRequest request) {
        CustomerQueryTool tool = new CustomerQueryTool();
        tool.customerApplicationService = customerApplicationService;
        tool.bindRuntimeRequest(request);
        return tool;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "只读查询当前用户有权访问的真实客户。"
                + "传入id时查询单条详情；不传id时按关键词、状态分页查询，结果按创建时间倒序。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("id", stringField("客户编号，可为空；编号必须使用字符串传递"));
        properties.put("keyword", stringField("客户名称、行业、联系人、电话或邮箱关键词，可为空"));
        properties.put(
                "status",
                stringField("客户状态，可为空：POTENTIAL、ACTIVE、DEALING、COOPERATED、"
                        + "SLEEPING、CHURNED、BLACKLIST"));
        properties.put("pageNo", integerField("页码，默认1", 1, 100000));
        properties.put("pageSize", integerField("每页数量，默认10，最大20", 1, 20));
        return objectSchema(properties);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        ToolResultBlock denied = validateRuntime("crm:customer:view", "crm:customer:manage");
        if (denied != null) {
            return Mono.just(denied);
        }
        try {
            Map<String, Object> input = input(param);
            Long id = optionalId(input.get("id"), "客户编号");
            if (id != null) {
                CustomerResponse response =
                        customerApplicationService.detail(tenantId(), userId(), dataScope(), id);
                return Mono.just(ToolResultBlock.text(
                        JSON.toJSONString(detailResult(toRecord(response)))));
            }
            CustomerQuery query = new CustomerQuery();
            query.setKeyword(text(input.get("keyword")));
            query.setStatus(optionalEnum(input.get("status"), CustomerStatus.class, "客户状态"));
            query.setPageNo(pageNo(input.get("pageNo")));
            query.setPageSize(pageSize(input.get("pageSize")));
            PageData<CustomerResponse> page =
                    customerApplicationService.page(tenantId(), userId(), dataScope(), query);
            List<JSONObject> records = new ArrayList<JSONObject>();
            for (CustomerResponse response : page.getRecords()) {
                records.add(toRecord(response));
            }
            return Mono.just(ToolResultBlock.text(JSON.toJSONString(pageResult(
                    page.getTotal(), page.getPageNo(), page.getPageSize(), records))));
        } catch (BusinessException ex) {
            return Mono.just(ToolResultBlock.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return Mono.just(ToolResultBlock.error(ex.getMessage()));
        }
    }

    private JSONObject toRecord(CustomerResponse response) {
        JSONObject record = new JSONObject();
        record.put("id", id(response.getId()));
        record.put("name", response.getName());
        record.put("industry", response.getIndustry());
        record.put("contactName", response.getContactName());
        record.put("contactPhone", response.getContactPhone());
        record.put("contactEmail", response.getContactEmail());
        record.put("level", response.getLevel());
        record.put("status", response.getStatus());
        record.put("ownerId", id(response.getOwnerId()));
        record.put("ownerName", response.getOwnerName());
        record.put("remark", shrink(response.getRemark(), 1000));
        record.put("aiSummary", shrink(response.getAiSummary(), 1600));
        record.put("aiAnalyzedAt", dateTime(response.getAiAnalyzedAt()));
        record.put("createdAt", dateTime(response.getCreatedAt()));
        record.put("updatedAt", dateTime(response.getUpdatedAt()));
        return record;
    }
}
