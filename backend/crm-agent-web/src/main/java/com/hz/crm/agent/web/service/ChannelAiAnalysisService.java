package com.hz.crm.agent.web.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.service.AgentDefinitionService;
import com.hz.crm.agent.runtime.service.AgentRuntimeFacade;
import com.hz.crm.agent.web.tool.ChannelAnalysisResultTool;
import com.hz.crm.application.channel.ChannelApplicationService;
import com.hz.crm.application.channel.dto.ChannelResponse;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.json.Jsons;
import com.hz.crm.domain.channel.ChannelStatus;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChannelAiAnalysisService {

    private static final String CHANNEL_ANALYZE_SCENE = "CHANNEL_ANALYZE";

    private static final String CHANNEL_RESULT_FUNCTION = "channel_analysis_result";

    @Autowired
    private ChannelApplicationService channelApplicationService;

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Autowired
    private AgentRuntimeFacade agentRuntimeFacade;

    public ChannelResponse analyze(Long tenantId, Long userId, String dataScope, Long channelId) {
        ChannelResponse channel = channelApplicationService.detail(tenantId, userId, dataScope, channelId);
        validateMaterial(channel);
        AgentEntity agent = agentDefinitionService.findEnabledByScene(tenantId, CHANNEL_ANALYZE_SCENE);
        if (agent == null) {
            throw new BusinessException("CHANNEL_AI_001", "请先在智能体配置中启用渠道内容分析场景智能体");
        }
        if (!StringUtils.hasText(agent.getApiKey())) {
            throw new BusinessException("CHANNEL_AI_002", "渠道内容分析智能体未配置模型密钥");
        }
        channelApplicationService.prepareAiAnalysis(tenantId, userId, dataScope, channelId);
        AgentRuntimeRequest runtimeRequest = buildRuntimeRequest(tenantId, userId, channel, agent);
        List<AgentRuntimeEvent> events = runAgent(runtimeRequest);
        JSONObject result = resolveCapturedResult(runtimeRequest);
        if (result == null && !hasToolCall(events, CHANNEL_RESULT_FUNCTION)) {
            throw new BusinessException("CHANNEL_AI_003", "渠道智能体未调用标准结果函数，请检查场景提示词");
        }
        if (result == null) {
            result = parseResult(resolveOutput(events));
        }
        if (result == null) {
            throw new BusinessException("CHANNEL_AI_003", "渠道智能体已调用结果函数，但函数参数无法解析");
        }
        normalizeResult(result, hasToolCall(events, "knowledge_search"));
        String remark = buildRemark(result);
        if (!StringUtils.hasText(remark)) {
            throw new BusinessException("CHANNEL_AI_004", "渠道智能体未生成可用的分析备注");
        }
        return channelApplicationService.completeAiAnalysis(
                tenantId,
                userId,
                dataScope,
                channelId,
                result.getString("summary"),
                buildUsefulInfo(result),
                remark,
                JSON.toJSONString(result),
                runtimeRequest.getRunId());
    }

    private void validateMaterial(ChannelResponse channel) {
        if (channel == null || channel.getId() == null) {
            throw new BusinessException("CHANNEL_AI_005", "渠道记录不存在");
        }
        if (channel.getLeadId() != null) {
            throw new BusinessException("CHANNEL_AI_006", "已晋升线索的渠道不能重新分析");
        }
        if (ChannelStatus.ANALYZED == channel.getStatus()
                && StringUtils.hasText(channel.getRemark())
                && channel.getAgentRunId() != null
                && channel.getAiAnalyzedAt() != null) {
            throw new BusinessException("CHANNEL_AI_010", "渠道材料已完成AI整理，无需重复分析");
        }
        if (!StringUtils.hasText(channel.getTranscriptText())
                && !StringUtils.hasText(channel.getSourceSnapshot())) {
            throw new BusinessException("CHANNEL_AI_007", "渠道材料尚未完成文本提取或中文转译");
        }
    }

    private AgentRuntimeRequest buildRuntimeRequest(
            Long tenantId, Long userId, ChannelResponse channel, AgentEntity agent) {
        AgentRuntimeRequest request = new AgentRuntimeRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setAgent(agent);
        request.setSceneCode(CHANNEL_ANALYZE_SCENE);
        request.setSessionId("channel-analysis-" + channel.getId());
        request.setBusinessType("CHANNEL");
        request.setBusinessId(String.valueOf(channel.getId()));
        request.setInjectedPrompt(buildInjectedPrompt());
        request.setMessage(buildMessage(channel));
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("businessType", "CHANNEL");
        context.put("channelId", String.valueOf(channel.getId()));
        context.put("conversationTitle", "渠道分析：" + resolveText(channel.getTitle(), String.valueOf(channel.getId())));
        request.setContext(context);
        return request;
    }

    private String buildInjectedPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("本次任务只处理当前渠道材料，禁止引用其他渠道、线索或会话中的信息。");
        builder.append("只能使用传入的真实渠道数据、工具真实返回和知识库真实命中，禁止编造联系人、预算、需求、风险或产品能力。");
        builder.append("判断产品定位或产品匹配时必须先调用knowledge_search；未命中时必须明确写知识库暂无匹配依据。");
        builder.append("存在公司名称时可调用customer_web_search补充公开基础信息，未确认字段不能猜测。");
        builder.append("最终动作必须且只能调用一次channel_analysis_result，九个字段必须直接作为函数参数传入，参数类型必须符合函数定义。");
        builder.append("禁止把函数参数包装成字符串，禁止输出channel_analysis_result(...)文本。");
        builder.append("函数调用成功后立即结束，禁止继续输出解释、Markdown、代码块或自然语言。");
        return builder.toString();
    }

    private String buildMessage(ChannelResponse channel) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("id", String.valueOf(channel.getId()));
        data.put("title", channel.getTitle());
        data.put("channelType", channel.getChannelType());
        data.put("source", channel.getSource());
        data.put("contactName", channel.getContactName());
        data.put("companyName", channel.getCompanyName());
        data.put("phone", channel.getPhone());
        data.put("email", channel.getEmail());
        data.put("mediaFileName", channel.getMediaFileName());
        data.put("transcriptText", shrink(channel.getTranscriptText(), 12000));
        data.put("sourceSnapshot", shrink(channel.getSourceSnapshot(), 12000));
        StringBuilder builder = new StringBuilder();
        builder.append("请整理以下渠道材料，提炼销售可直接使用的备注。");
        builder.append("必须覆盖基础信息、产品定位、购买意向及依据、关键信息、风险和下一步动作。");
        builder.append("产品定位必须结合knowledge_search结果；资料不足的字段明确标记未确认，不能补造。");
        builder.append("最终直接调用channel_analysis_result并传入结构化参数，不要用文本模拟函数调用。");
        builder.append("\n渠道真实数据：").append(Jsons.toJson(data));
        return builder.toString();
    }

    private List<AgentRuntimeEvent> runAgent(AgentRuntimeRequest runtimeRequest) {
        List<AgentRuntimeEvent> events = agentRuntimeFacade
                .run(runtimeRequest)
                .collectList()
                .block(Duration.ofSeconds(120));
        if (events == null || events.isEmpty()) {
            throw new BusinessException("CHANNEL_AI_008", "渠道智能体未返回分析结果");
        }
        return events;
    }

    private JSONObject resolveCapturedResult(AgentRuntimeRequest runtimeRequest) {
        if (runtimeRequest == null || runtimeRequest.getContext() == null) {
            return null;
        }
        Object value = runtimeRequest.getContext().remove(ChannelAnalysisResultTool.RESULT_CONTEXT_KEY);
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String resolveOutput(List<AgentRuntimeEvent> events) {
        StringBuilder toolResult = new StringBuilder();
        String channelToolResult = null;
        for (AgentRuntimeEvent event : events) {
            if (event == null) {
                continue;
            }
            String type = event.getType() == null ? "" : event.getType().toUpperCase();
            if (type.contains("TOOL_RESULT_START")) {
                toolResult.setLength(0);
                continue;
            }
            if (type.contains("TOOL_RESULT_TEXT_DELTA") && StringUtils.hasText(event.getContent())) {
                toolResult.append(event.getContent());
                continue;
            }
            if (type.contains("TOOL_RESULT_END")) {
                if (CHANNEL_RESULT_FUNCTION.equals(event.getToolName())) {
                    channelToolResult = toolResult.toString();
                }
                toolResult.setLength(0);
            }
        }
        if (StringUtils.hasText(channelToolResult)) {
            return channelToolResult;
        }
        for (int index = events.size() - 1; index >= 0; index--) {
            AgentRuntimeEvent event = events.get(index);
            if (event != null
                    && StringUtils.hasText(event.getContent())
                    && (CHANNEL_RESULT_FUNCTION.equals(event.getToolName())
                    || event.getContent().contains(CHANNEL_RESULT_FUNCTION))) {
                return event.getContent();
            }
        }
        for (int index = events.size() - 1; index >= 0; index--) {
            AgentRuntimeEvent event = events.get(index);
            if (event == null || !StringUtils.hasText(event.getContent())) {
                continue;
            }
            String type = event.getType() == null ? "" : event.getType().toUpperCase();
            if (type.contains("RESULT") || type.contains("FINAL")) {
                return event.getContent();
            }
        }
        return "";
    }

    private JSONObject parseResult(String output) {
        if (!StringUtils.hasText(output)) {
            return null;
        }
        String text = output.trim();
        JSONObject direct = parseJsonObject(text);
        if (direct != null) {
            return direct;
        }
        int functionIndex = text.indexOf(CHANNEL_RESULT_FUNCTION);
        int start = functionIndex < 0 ? text.indexOf('{') : text.indexOf('{', functionIndex);
        int end = findJsonEnd(text, start);
        if (start >= 0 && end > start) {
            JSONObject wrapped = parseJsonObject(text.substring(start, end + 1));
            if (wrapped != null) {
                return wrapped;
            }
        }
        try {
            String decoded = JSON.parseObject(text, String.class);
            if (!StringUtils.hasText(decoded) || decoded.equals(text)) {
                return null;
            }
            return parseResult(decoded);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private JSONObject parseJsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return JSON.parseObject(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private int findJsonEnd(String text, int start) {
        if (start < 0) {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char value = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (value == '\\') {
                escaped = true;
                continue;
            }
            if (value == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private boolean hasToolCall(List<AgentRuntimeEvent> events, String toolName) {
        if (events == null || !StringUtils.hasText(toolName)) {
            return false;
        }
        for (AgentRuntimeEvent event : events) {
            if (event != null && toolName.equals(event.getToolName())) {
                return true;
            }
        }
        return false;
    }

    private void normalizeResult(JSONObject result, boolean knowledgeSearched) {
        result.put("conclusionTitle", shrink(result.getString("conclusionTitle"), 40));
        result.put("summary", shrink(result.getString("summary"), 600));
        result.put("productPositioning", shrink(result.getString("productPositioning"), 1000));
        result.put("purchaseIntent", resolvePurchaseIntent(result.getString("purchaseIntent")));
        result.put("intentBasis", shrink(result.getString("intentBasis"), 1000));
        result.put("basicInformation", normalizeArray(result.getJSONArray("basicInformation"), 6, 160));
        result.put("keyFindings", normalizeArray(result.getJSONArray("keyFindings"), 5, 200));
        result.put("riskWarnings", normalizeArray(result.getJSONArray("riskWarnings"), 5, 200));
        result.put("nextActions", normalizeArray(result.getJSONArray("nextActions"), 5, 200));
        if (!knowledgeSearched) {
            result.put("productPositioning", "本次未完成知识库检索，暂不能确认产品定位");
            JSONArray risks = result.getJSONArray("riskWarnings");
            if (risks.size() < 5) {
                risks.add("产品定位尚未经过公司知识库验证");
            }
        }
    }

    private JSONArray normalizeArray(JSONArray source, int maxSize, int maxLength) {
        JSONArray values = new JSONArray();
        if (source == null) {
            return values;
        }
        for (int index = 0; index < source.size() && values.size() < maxSize; index++) {
            String value = shrink(source.getString(index), maxLength);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private String resolvePurchaseIntent(String value) {
        if ("HIGH".equals(value) || "MEDIUM".equals(value) || "LOW".equals(value)) {
            return value;
        }
        return "UNKNOWN";
    }

    private String buildRemark(JSONObject result) {
        StringBuilder builder = new StringBuilder();
        builder.append("## 渠道智能分析");
        appendSection(builder, "销售结论", joinTexts(
                result.getString("conclusionTitle"),
                result.getString("summary")));
        appendListSection(builder, "基础信息", result.getJSONArray("basicInformation"), "暂无可确认的基础信息");
        appendSection(builder, "产品定位", resolveText(
                result.getString("productPositioning"),
                "知识库暂无可确认的产品匹配依据"));
        appendSection(builder, "购买意向", "**"
                + purchaseIntentText(result.getString("purchaseIntent"))
                + "**\n\n"
                + resolveText(result.getString("intentBasis"), "当前材料不足，暂不能确认购买意向"));
        appendListSection(builder, "关键信息", result.getJSONArray("keyFindings"), "暂未提取到更多关键信息");
        appendListSection(builder, "风险与信息缺口", result.getJSONArray("riskWarnings"), "暂未识别到明确风险");
        appendListSection(builder, "建议下一步", result.getJSONArray("nextActions"), "补充客户需求后再进行判断");
        return builder.toString();
    }

    private String buildUsefulInfo(JSONObject result) {
        StringBuilder builder = new StringBuilder();
        appendPlainList(builder, result.getJSONArray("basicInformation"));
        appendPlainList(builder, result.getJSONArray("keyFindings"));
        return builder.toString();
    }

    private void appendSection(StringBuilder builder, String title, String content) {
        builder.append("\n\n### ").append(title).append("\n\n").append(content);
    }

    private void appendListSection(StringBuilder builder, String title, JSONArray values, String emptyText) {
        builder.append("\n\n### ").append(title).append("\n\n");
        if (values == null || values.isEmpty()) {
            builder.append("- ").append(emptyText);
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            builder.append("- ").append(values.getString(index));
            if (index < values.size() - 1) {
                builder.append('\n');
            }
        }
    }

    private void appendPlainList(StringBuilder builder, JSONArray values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("- ").append(values.getString(index));
        }
    }

    private String purchaseIntentText(String value) {
        if ("HIGH".equals(value)) {
            return "高意向";
        }
        if ("MEDIUM".equals(value)) {
            return "中意向";
        }
        if ("LOW".equals(value)) {
            return "低意向";
        }
        return "待确认";
    }

    private String joinTexts(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return resolveText(second, "暂未形成明确销售结论");
        }
        if (!StringUtils.hasText(second)) {
            return first;
        }
        return "**" + first + "**\n\n" + second;
    }

    private String resolveText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : second;
    }

    private String shrink(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
