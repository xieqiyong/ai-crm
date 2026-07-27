package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
public class CompanyWebSearchTool implements AgentTool {

    @Autowired
    private CompanyWebSearchService companyWebSearchService;

    @Override
    public String getName() {
        return "customer_web_search";
    }

    @Override
    public String getDescription() {
        return "根据公司名称搜索互联网公开信息，优先从爱企查等企业详情页提取profileDraft客户档案，用于补充负责人、规模、行业、电话、官网、地址等信息。没有搜索结果时必须如实返回空结果。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("companyName", stringField("公司名称，必须来自当前线索真实数据"));
        properties.put("keywords", stringField("附加搜索关键词，可为空字符串"));
        properties.put("limit", integerField("返回结果数量，1到10"));
        properties.put("fetchDetail", booleanField("是否对搜索结果进行二次详情抓取，默认true"));

        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", values("companyName"));
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Map<String, Object> input = param == null ? null : param.getInput();
        if (input == null || blank(text(input.get("companyName")))) {
            return Mono.just(ToolResultBlock.error("公司名称不能为空"));
        }
        String companyName = text(input.get("companyName"));
        String keywords = text(input.get("keywords"));
        Integer limit = integer(input.get("limit"));
        Boolean fetchDetail = bool(input.get("fetchDetail"));
        JSONObject result = companyWebSearchService.search(companyName, keywords, limit, fetchDetail);
        return Mono.just(ToolResultBlock.text(JSON.toJSONString(result)));
    }

    private Map<String, Object> stringField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "string");
        field.put("description", description);
        return field;
    }

    private Map<String, Object> integerField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "integer");
        field.put("description", description);
        field.put("minimum", 1);
        field.put("maximum", 10);
        return field;
    }

    private Map<String, Object> booleanField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "boolean");
        field.put("description", description);
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

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private Boolean bool(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
