package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSON;
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
public class LeadAnalysisResultTool implements AgentTool {

    @Override
    public String getName() {
        return "lead_analysis_result";
    }

    @Override
    public String getDescription() {
        return "提交线索分析的最终结构化结果。调用后不要再输出其他内容。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("conclusionTitle", stringField("20字以内的销售结论标题"));
        properties.put("salesConclusion", stringField("80字以内的销售可读结论"));
        properties.put("stage", enumField("线索阶段", values("NEW", "FOLLOWING", "QUALIFIED", "CONVERTED", "CLOSED", "UNKNOWN")));
        properties.put("priority", enumField("销售优先级", values("HIGH", "MEDIUM", "LOW")));
        properties.put("recommendConvert", booleanField("是否建议推进转化"));
        properties.put("score", integerField("0到100的线索评分"));
        properties.put("confidence", numberField("0到1的置信度"));
        properties.put("keyFindings", stringArrayField("关键证据，最多4项，每项不超过60字"));
        properties.put("riskWarnings", stringArrayField("风险提醒，最多4项，每项不超过60字"));
        properties.put("nextActions", stringArrayField("下一步动作，最多4项，每项不超过60字"));
        properties.put("reason", stringField("详细依据，只能基于真实线索数据"));
        properties.put("nextAction", stringField("最重要的下一步动作"));
        properties.put("convertDraft", convertDraftField());
        properties.put("customerProfile", customerProfileField());

        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", values(
                "conclusionTitle",
                "salesConclusion",
                "stage",
                "priority",
                "recommendConvert",
                "score",
                "confidence",
                "keyFindings",
                "riskWarnings",
                "nextActions",
                "reason",
                "nextAction",
                "convertDraft",
                "customerProfile"));
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Map<String, Object> input = param == null ? null : param.getInput();
        if (input == null || input.isEmpty()) {
            return Mono.just(ToolResultBlock.error("线索分析结果不能为空"));
        }
        return Mono.just(ToolResultBlock.text("lead_analysis_result(" + JSON.toJSONString(input) + ")"));
    }

    private Map<String, Object> stringField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "string");
        field.put("description", description);
        return field;
    }

    private Map<String, Object> booleanField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "boolean");
        field.put("description", description);
        return field;
    }

    private Map<String, Object> integerField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "integer");
        field.put("description", description);
        field.put("minimum", 0);
        field.put("maximum", 100);
        return field;
    }

    private Map<String, Object> numberField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "number");
        field.put("description", description);
        field.put("minimum", 0);
        field.put("maximum", 1);
        return field;
    }

    private Map<String, Object> enumField(String description, List<String> values) {
        Map<String, Object> field = stringField(description);
        field.put("enum", values);
        return field;
    }

    private Map<String, Object> stringArrayField(String description) {
        return stringArrayField(description, 4);
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

    private Map<String, Object> convertDraftField() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("customerName", stringField("建议客户名称，无法确认则为空字符串"));
        properties.put("industry", stringField("行业，无法确认则为空字符串"));
        properties.put("contactName", stringField("联系人，无法确认则为空字符串"));
        properties.put("contactPhone", stringField("联系电话，无法确认则为空字符串"));
        properties.put("contactEmail", stringField("联系邮箱，无法确认则为空字符串"));
        properties.put("level", enumField("客户级别", values("NORMAL", "IMPORTANT", "STRATEGIC")));
        properties.put("status", stringField("客户状态，优先使用POTENTIAL"));
        properties.put("remark", stringField("转客户备注"));

        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "object");
        field.put("properties", properties);
        field.put("required", values(
                "customerName",
                "industry",
                "contactName",
                "contactPhone",
                "contactEmail",
                "level",
                "status",
                "remark"));
        return field;
    }

    private Map<String, Object> customerProfileField() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("available", booleanField("是否检索到可用于客户档案的公开信息"));
        properties.put("companyName", stringField("公司名称，必须来自线索或搜索结果"));
        properties.put("legalRepresentative", stringField("法定代表人或公开负责人，无法确认则为空字符串"));
        properties.put("keyPerson", stringField("关键联系人或公开高管，无法确认则为空字符串"));
        properties.put("companyScale", stringField("公司规模，无法确认则为空字符串"));
        properties.put("industry", stringField("公司行业，无法确认则为空字符串"));
        properties.put("phone", stringField("公开电话，无法确认则为空字符串"));
        properties.put("email", stringField("公开邮箱，无法确认则为空字符串"));
        properties.put("website", stringField("官网，无法确认则为空字符串"));
        properties.put("address", stringField("公开地址，无法确认则为空字符串"));
        properties.put("registeredCapital", stringField("注册资本，无法确认则为空字符串"));
        properties.put("sourceSummary", stringField("客户档案来源摘要，只能基于搜索结果"));
        properties.put("searchedAt", stringField("搜索时间，无法确认则为空字符串"));
        properties.put("sourceUrls", stringArrayField("公开来源链接，最多6项", 6));

        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "object");
        field.put("properties", properties);
        field.put("required", values(
                "available",
                "companyName",
                "legalRepresentative",
                "keyPerson",
                "companyScale",
                "industry",
                "phone",
                "email",
                "website",
                "address",
                "registeredCapital",
                "sourceSummary",
                "searchedAt",
                "sourceUrls"));
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
