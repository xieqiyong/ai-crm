package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.application.lead.LeadApplicationService;
import com.hz.crm.application.lead.dto.LeadQuery;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.lead.LeadStatus;
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
public class LeadQueryTool extends CrmQueryToolSupport implements AgentTool {

    public static final String TOOL_NAME = "crm_query_leads";

    @Autowired
    private LeadApplicationService leadApplicationService;

    public LeadQueryTool bind(AgentRuntimeRequest request) {
        LeadQueryTool tool = new LeadQueryTool();
        tool.leadApplicationService = leadApplicationService;
        tool.bindRuntimeRequest(request);
        return tool;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "只读查询当前用户有权访问的真实线索。"
                + "传入id时查询单条详情；不传id时按关键词、状态分页查询，结果按创建时间倒序。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("id", stringField("线索编号，可为空；编号必须使用字符串传递"));
        properties.put("keyword", stringField("线索名称、公司、电话、邮箱或来源关键词，可为空"));
        properties.put(
                "status",
                stringField("线索状态，可为空：NEW、CONTACTED、FOLLOWING、QUALIFIED、"
                        + "NURTURING、CONVERTED、INVALID、DUPLICATE、CLOSED"));
        properties.put("pageNo", integerField("页码，默认1", 1, 100000));
        properties.put("pageSize", integerField("每页数量，默认10，最大20", 1, 20));
        return objectSchema(properties);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        ToolResultBlock denied = validateRuntime("crm:lead:view", "crm:lead:manage");
        if (denied != null) {
            return Mono.just(denied);
        }
        try {
            Map<String, Object> input = input(param);
            Long id = optionalId(input.get("id"), "线索编号");
            if (id != null) {
                LeadResponse response =
                        leadApplicationService.detail(tenantId(), userId(), dataScope(), id);
                return Mono.just(ToolResultBlock.text(
                        JSON.toJSONString(detailResult(toRecord(response)))));
            }
            LeadQuery query = new LeadQuery();
            query.setKeyword(text(input.get("keyword")));
            query.setStatus(optionalEnum(input.get("status"), LeadStatus.class, "线索状态"));
            query.setPageNo(pageNo(input.get("pageNo")));
            query.setPageSize(pageSize(input.get("pageSize")));
            PageData<LeadResponse> page =
                    leadApplicationService.page(tenantId(), userId(), dataScope(), query);
            List<JSONObject> records = new ArrayList<JSONObject>();
            for (LeadResponse response : page.getRecords()) {
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

    private JSONObject toRecord(LeadResponse response) {
        JSONObject record = new JSONObject();
        record.put("id", id(response.getId()));
        record.put("name", response.getName());
        record.put("companyName", response.getCompanyName());
        record.put("phone", response.getPhone());
        record.put("email", response.getEmail());
        record.put("source", response.getSource());
        record.put("status", response.getStatus());
        record.put("customerId", id(response.getCustomerId()));
        record.put("customerName", response.getCustomerName());
        record.put("ownerId", id(response.getOwnerId()));
        record.put("ownerName", response.getOwnerName());
        record.put("remark", shrink(response.getRemark(), 1000));
        record.put("aiSummary", shrink(response.getAiSummary(), 1600));
        record.put("aiConfidence", response.getAiConfidence());
        record.put("aiAnalyzedAt", dateTime(response.getAiAnalyzedAt()));
        record.put("convertedAt", dateTime(response.getConvertedAt()));
        record.put("createdAt", dateTime(response.getCreatedAt()));
        record.put("updatedAt", dateTime(response.getUpdatedAt()));
        return record;
    }
}
