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
public class ChannelAnalysisResultTool implements AgentTool {

    public static final String RESULT_CONTEXT_KEY = "channelAnalysisResult";

    private AgentRuntimeRequest runtimeRequest;

    public ChannelAnalysisResultTool bind(AgentRuntimeRequest request) {
        ChannelAnalysisResultTool tool = new ChannelAnalysisResultTool();
        tool.runtimeRequest = request;
        return tool;
    }

    @Override
    public String getName() {
        return "channel_analysis_result";
    }

    @Override
    public String getDescription() {
        return "提交渠道材料分析的最终结构化结果。调用后不要再输出其他内容。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("conclusionTitle", stringField("20字以内的销售结论标题"));
        properties.put("summary", stringField("120字以内的渠道材料总结"));
        properties.put("basicInformation", stringArrayField("材料中可确认的基础信息，最多6项", 6));
        properties.put("productPositioning", stringField("结合知识库判断的产品定位或匹配方向，无法确认时如实说明"));
        properties.put("purchaseIntent", enumField(
                "购买意向等级",
                values("HIGH", "MEDIUM", "LOW", "UNKNOWN")));
        properties.put("intentBasis", stringField("购买意向判断依据，只能引用真实材料"));
        properties.put("keyFindings", stringArrayField("关键业务信息，最多5项", 5));
        properties.put("riskWarnings", stringArrayField("风险和信息缺口，最多5项", 5));
        properties.put("nextActions", stringArrayField("销售下一步动作，最多5项", 5));

        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", values(
                "conclusionTitle",
                "summary",
                "basicInformation",
                "productPositioning",
                "purchaseIntent",
                "intentBasis",
                "keyFindings",
                "riskWarnings",
                "nextActions"));
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Map<String, Object> input = param == null ? null : param.getInput();
        if (input == null || input.isEmpty()) {
            return Mono.just(ToolResultBlock.error("渠道分析结果不能为空"));
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
