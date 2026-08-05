package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CustomerDeepSummaryResultTool implements AgentTool {

    public static final String RESULT_CONTEXT_KEY = "customerDeepSummaryResult";

    private AgentRuntimeRequest runtimeRequest;

    public CustomerDeepSummaryResultTool bind(AgentRuntimeRequest request) {
        CustomerDeepSummaryResultTool tool = new CustomerDeepSummaryResultTool();
        tool.runtimeRequest = request;
        return tool;
    }

    @Override
    public String getName() {
        return "customer_deep_summary_result";
    }

    @Override
    public String getDescription() {
        return "提交客户深度总结的最终结构化结果。调用后不要再输出其他内容。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("conclusionTitle", stringField("20字以内的客户结论标题"));
        properties.put("summary", stringField("160字以内的客户深度总结"));
        properties.put("customerJudgement", stringField("客户当前价值、阶段和销售判断"));
        properties.put("priority", enumField("客户经营优先级", values("HIGH", "MEDIUM", "LOW")));
        properties.put("keyFindings", stringArrayField("关键事实，最多6项，每项不超过80字", 6));
        properties.put("opportunityInsights", stringArrayField("商机洞察，最多5项，每项不超过80字", 5));
        properties.put("followupInsights", stringArrayField("跟进洞察，最多5项，每项不超过80字", 5));
        properties.put("riskWarnings", stringArrayField("风险提醒，最多5项，每项不超过80字", 5));
        properties.put("nextActions", stringArrayField("下一步动作，最多5项，每项不超过80字", 5));
        properties.put("missingData", stringArrayField("缺失信息，最多5项，每项不超过80字", 5));

        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", values(
                "conclusionTitle",
                "summary",
                "customerJudgement",
                "priority",
                "keyFindings",
                "opportunityInsights",
                "followupInsights",
                "riskWarnings",
                "nextActions",
                "missingData"));
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Map<String, Object> input = param == null ? null : param.getInput();
        if (input == null || input.isEmpty()) {
            return Mono.just(ToolResultBlock.error("客户深度总结结果不能为空"));
        }
        JSONObject result = JSON.parseObject(JSON.toJSONString(input));
        if (runtimeRequest != null && runtimeRequest.getContext() != null) {
            runtimeRequest.getContext().put(RESULT_CONTEXT_KEY, result);
        }
        return Mono.just(ToolResultBlock.text(JSON.toJSONString(result)));
    }

    private Map<String, Object> stringField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "string");
        field.put("description", description);
        return field;
    }

    private Map<String, Object> enumField(String description, List<String> values) {
        Map<String, Object> field = stringField(description);
        field.put("enum", values);
        return field;
    }

    private Map<String, Object> stringArrayField(String description, int maxItems) {
        Map<String, Object> items = new LinkedHashMap<String, Object>();
        items.put("type", "string");

        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "array");
        field.put("description", description);
        field.put("items", items);
        field.put("maxItems", maxItems);
        return field;
    }

    private List<String> values(String... values) {
        List<String> list = new ArrayList<String>();
        if (values == null) {
            return list;
        }
        for (String value : values) {
            list.add(value);
        }
        return list;
    }
}
