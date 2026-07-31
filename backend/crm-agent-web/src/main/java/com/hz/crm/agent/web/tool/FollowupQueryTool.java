package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.application.followup.FollowupApplicationService;
import com.hz.crm.application.followup.dto.FollowupQuery;
import com.hz.crm.application.followup.dto.FollowupResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.followup.FollowupType;
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
public class FollowupQueryTool extends CrmQueryToolSupport implements AgentTool {

    public static final String TOOL_NAME = "crm_query_followups";

    @Autowired
    private FollowupApplicationService followupApplicationService;

    public FollowupQueryTool bind(AgentRuntimeRequest request) {
        FollowupQueryTool tool = new FollowupQueryTool();
        tool.followupApplicationService = followupApplicationService;
        tool.bindRuntimeRequest(request);
        return tool;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "只读查询当前用户有权访问的真实跟进记录。"
                + "传入id时查询单条详情；不传id时可按关联对象、跟进方式和关键词分页查询。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("id", stringField("跟进记录编号，可为空；编号必须使用字符串传递"));
        properties.put("keyword", stringField("关联对象名称、跟进内容、结果或下次计划关键词，可为空"));
        properties.put("targetType", stringField("关联对象类型，可为空：LEAD、CUSTOMER、OPPORTUNITY"));
        properties.put("targetId", stringField("关联对象编号，可为空；编号必须使用字符串传递"));
        properties.put("followupType", stringField("跟进方式，可为空：PHONE、WECHAT、EMAIL、MEETING、VISIT、OTHER"));
        properties.put("pageNo", integerField("页码，默认1", 1, 100000));
        properties.put("pageSize", integerField("每页数量，默认10，最大20", 1, 20));
        return objectSchema(properties);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        ToolResultBlock denied = validateRuntime("crm:followup:view", "crm:followup:manage");
        if (denied != null) {
            return Mono.just(denied);
        }
        try {
            Map<String, Object> input = input(param);
            Long id = optionalId(input.get("id"), "跟进记录编号");
            if (id != null) {
                FollowupResponse response =
                        followupApplicationService.detail(tenantId(), userId(), dataScope(), id);
                return Mono.just(ToolResultBlock.text(
                        JSON.toJSONString(detailResult(toRecord(response)))));
            }
            FollowupQuery query = new FollowupQuery();
            query.setKeyword(text(input.get("keyword")));
            query.setTargetType(optionalEnum(
                    input.get("targetType"), FollowupTargetType.class, "关联对象类型"));
            query.setTargetId(optionalId(input.get("targetId"), "关联对象编号"));
            query.setFollowupType(optionalEnum(
                    input.get("followupType"), FollowupType.class, "跟进方式"));
            query.setPageNo(pageNo(input.get("pageNo")));
            query.setPageSize(pageSize(input.get("pageSize")));
            PageData<FollowupResponse> page =
                    followupApplicationService.page(tenantId(), userId(), dataScope(), query);
            List<JSONObject> records = new ArrayList<JSONObject>();
            for (FollowupResponse response : page.getRecords()) {
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

    private JSONObject toRecord(FollowupResponse response) {
        JSONObject record = new JSONObject();
        record.put("id", id(response.getId()));
        record.put("targetType", response.getTargetType());
        record.put("targetId", id(response.getTargetId()));
        record.put("targetName", response.getTargetName());
        record.put("followupType", response.getFollowupType());
        record.put("followupAt", dateTime(response.getFollowupAt()));
        record.put("content", plainText(response.getContent(), 2000));
        record.put("result", shrink(response.getResult(), 1000));
        record.put("nextPlan", shrink(response.getNextPlan(), 1000));
        record.put("nextFollowTime", dateTime(response.getNextFollowTime()));
        record.put("ownerId", id(response.getOwnerId()));
        record.put("ownerName", response.getOwnerName());
        record.put("createdAt", dateTime(response.getCreatedAt()));
        record.put("updatedAt", dateTime(response.getUpdatedAt()));
        return record;
    }
}
