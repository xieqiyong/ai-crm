package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.knowledge.dto.KnowledgeSearchHit;
import com.hz.crm.knowledge.dto.KnowledgeSearchRequest;
import com.hz.crm.knowledge.dto.KnowledgeSearchResponse;
import com.hz.crm.knowledge.service.KnowledgeDocumentService;
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
public class KnowledgeSearchTool implements AgentTool {

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    private AgentRuntimeRequest runtimeRequest;

    public KnowledgeSearchTool bind(AgentRuntimeRequest request) {
        KnowledgeSearchTool tool = new KnowledgeSearchTool();
        tool.knowledgeDocumentService = knowledgeDocumentService;
        tool.runtimeRequest = request;
        return tool;
    }

    @Override
    public String getName() {
        return "knowledge_search";
    }

    @Override
    public String getDescription() {
        return "调用公司知识库混合检索，查询产品定位、解决方案、客户案例、销售话术和FAQ。"
                + "返回内容只能作为已入库知识引用，检索不到时必须说明知识库暂无资料。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("query", stringField("检索问题或关键词"));
        properties.put("category", stringField("知识分类，可为空字符串"));
        properties.put("sourceType", stringField("知识来源类型，可为空字符串"));
        properties.put("topK", integerField("最终返回数量，1到20", 1, 20));
        properties.put("vectorCandidates", integerField("向量召回候选数，默认使用系统配置", 1, 100));
        properties.put("keywordCandidates", integerField("关键词召回候选数，默认使用系统配置", 1, 100));
        properties.put("databaseCandidates", integerField("数据库兜底候选数，默认使用系统配置", 1, 100));
        properties.put("vectorWeight", numberField("向量召回权重，0到1，默认使用系统配置"));
        properties.put("keywordWeight", numberField("关键词召回权重，0到1，默认使用系统配置"));
        properties.put("databaseWeight", numberField("数据库兜底权重，0到1，默认使用系统配置"));
        properties.put("minScore", numberField("最低融合分，0到1，默认不过滤"));
        properties.put("databaseFallbackOnly", booleanField("数据库是否只在向量和关键词无结果时兜底"));

        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", values("query"));
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        if (runtimeRequest == null || runtimeRequest.getTenantId() == null) {
            return Mono.just(ToolResultBlock.error("知识库检索缺少租户上下文"));
        }
        Map<String, Object> input = param == null ? null : param.getInput();
        if (input == null || blank(text(input.get("query")))) {
            return Mono.just(ToolResultBlock.error("知识库检索内容不能为空"));
        }
        KnowledgeSearchRequest request = new KnowledgeSearchRequest();
        request.setQuery(text(input.get("query")));
        request.setCategory(text(input.get("category")));
        request.setSourceType(text(input.get("sourceType")));
        request.setTopK(integer(input.get("topK")));
        request.setVectorCandidates(integer(input.get("vectorCandidates")));
        request.setKeywordCandidates(integer(input.get("keywordCandidates")));
        request.setDatabaseCandidates(integer(input.get("databaseCandidates")));
        request.setVectorWeight(number(input.get("vectorWeight")));
        request.setKeywordWeight(number(input.get("keywordWeight")));
        request.setDatabaseWeight(number(input.get("databaseWeight")));
        request.setMinScore(number(input.get("minScore")));
        request.setDatabaseFallbackOnly(bool(input.get("databaseFallbackOnly")));
        KnowledgeSearchResponse response =
                knowledgeDocumentService.search(runtimeRequest.getTenantId(), request);
        JSONObject result = new JSONObject();
        result.put("query", response.getQuery());
        result.put("message", response.getMessage());
        Integer hitCount = response.getHits() == null
                ? Integer.valueOf(0)
                : Integer.valueOf(response.getHits().size());
        result.put("hitCount", hitCount);
        result.put("references", buildReferences(response.getHits()));
        result.put("usageRule", "只吸收资料摘要用于回答，不要向用户输出本JSON结构和检索明细。");
        result.put("retrieval", buildRetrieval(response));
        return Mono.just(ToolResultBlock.text(JSON.toJSONString(result)));
    }

    private List<JSONObject> buildReferences(List<KnowledgeSearchHit> hits) {
        List<JSONObject> references = new ArrayList<JSONObject>();
        if (hits == null || hits.isEmpty()) {
            return references;
        }
        int limit = Math.min(hits.size(), 5);
        for (int i = 0; i < limit; i++) {
            KnowledgeSearchHit hit = hits.get(i);
            if (hit == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("title", shrink(hit.getTitle(), 80));
            item.put("category", shrink(hit.getCategory(), 40));
            item.put("sourceType", shrink(hit.getSourceType(), 40));
            item.put("sourceUrl", shrink(hit.getSourceUrl(), 180));
            item.put("summary", shrink(hit.getContent(), 260));
            item.put("score", hit.getHybridScore() == null ? hit.getScore() : hit.getHybridScore());
            item.put("matchChannels", shrink(hit.getMatchChannels(), 80));
            references.add(item);
        }
        return references;
    }

    private JSONObject buildRetrieval(KnowledgeSearchResponse response) {
        JSONObject value = new JSONObject();
        value.put("searchMode", response.getSearchMode());
        value.put("embeddingEnabled", response.isEmbeddingEnabled());
        value.put("milvusEnabled", response.isMilvusEnabled());
        value.put("elasticsearchEnabled", response.isElasticsearchEnabled());
        value.put("databaseFallbackUsed", response.isDatabaseFallbackUsed());
        value.put("vectorCandidates", response.getVectorCandidates());
        value.put("keywordCandidates", response.getKeywordCandidates());
        value.put("databaseCandidates", response.getDatabaseCandidates());
        value.put("vectorWeight", response.getVectorWeight());
        value.put("keywordWeight", response.getKeywordWeight());
        value.put("databaseWeight", response.getDatabaseWeight());
        value.put("minScore", response.getMinScore());
        return value;
    }

    private Map<String, Object> stringField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "string");
        field.put("description", description);
        return field;
    }

    private Map<String, Object> integerField(String description, int minimum, int maximum) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "integer");
        field.put("description", description);
        field.put("minimum", minimum);
        field.put("maximum", maximum);
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

    private Double number(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (RuntimeException ex) {
            return null;
        }
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

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private String shrink(String value, int maxLength) {
        String text = normalize(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
