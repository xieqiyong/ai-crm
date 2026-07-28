package com.hz.crm.agent.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.service.AgentDefinitionService;
import com.hz.crm.agent.runtime.service.AgentRuntimeFacade;
import com.hz.crm.agent.web.dto.MarketingAssistantActionResponse;
import com.hz.crm.agent.web.dto.MarketingAssistantChatRequest;
import com.hz.crm.agent.web.dto.MarketingAssistantChatResponse;
import com.hz.crm.auth.security.CurrentUserContext;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.json.Jsons;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.MarketingFormEntity;
import com.hz.crm.domain.channel.MarketingFormStatus;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.channel.mapper.MarketingFormMapper;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerStatus;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadStatus;
import com.hz.crm.domain.lead.mapper.LeadMapper;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityStage;
import com.hz.crm.domain.opportunity.mapper.OpportunityMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class MarketingAssistantChatService {

    private static final String GENERAL_ASSISTANT_SCENE = "GENERAL_ASSISTANT";

    @Autowired
    private LeadMapper leadMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private OpportunityMapper opportunityMapper;

    @Autowired
    private ChannelRecordMapper channelRecordMapper;

    @Autowired
    private MarketingFormMapper marketingFormMapper;

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Autowired
    private AgentRuntimeFacade agentRuntimeFacade;

    public MarketingAssistantChatResponse chat(
            Long tenantId, Long userId, String dataScope, MarketingAssistantChatRequest request) {
        MarketingAssistantChatRequest safeRequest = request == null ? new MarketingAssistantChatRequest() : request;
        String scenario = resolveScenario(safeRequest);
        MarketingAssistantChatResponse fallback = ruleAssistant(tenantId, userId, dataScope, safeRequest, scenario);
        AgentEntity agent = resolveAgent(tenantId, fallback);
        if (agent == null && fallback.getMessage() != null) {
            return fallback;
        }
        if (!canRunAgent(agent)) {
            fallback.setAvailable(false);
            fallback.setSuccess(true);
            fallback.setMessage("未配置通用营销助手智能体，已返回真实数据规则建议");
            return fallback;
        }
        AgentRuntimeRequest runtimeRequest = null;
        try {
            runtimeRequest = buildRuntimeRequest(tenantId, userId, dataScope, safeRequest, scenario, fallback, agent);
            List<AgentRuntimeEvent> events = agentRuntimeFacade
                    .run(runtimeRequest)
                    .collectList()
                    .block(Duration.ofSeconds(90));
            return buildAiResponse(scenario, fallback, runtimeRequest, events);
        } catch (RuntimeException ex) {
            fallback.setAvailable(true);
            fallback.setSuccess(false);
            fallback.setMessage("AI营销助手调用失败，已返回真实数据规则建议：" + ex.getMessage());
            if (runtimeRequest != null) {
                fallback.setRunId(runtimeRequest.getRunId());
                fallback.setConversationId(runtimeRequest.getConversationId());
            }
            return fallback;
        }
    }

    public SseEmitter chatStream(
            Long tenantId, Long userId, String dataScope, MarketingAssistantChatRequest request) {
        SseEmitter emitter = new SseEmitter(Long.valueOf(120000L));
        SecurityContext securityContext = SecurityContextHolder.getContext();
        JwtPrincipal principal = CurrentUserContext.getPrincipal();
        String token = CurrentUserContext.getToken();
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                SecurityContextHolder.setContext(securityContext);
                if (principal != null) {
                    CurrentUserContext.setPrincipal(principal);
                }
                if (StringUtils.hasText(token)) {
                    CurrentUserContext.setToken(token);
                }
                try {
                    doChatStream(tenantId, userId, dataScope, request, emitter);
                } finally {
                    CurrentUserContext.clear();
                    SecurityContextHolder.clearContext();
                }
            }
        });
        return emitter;
    }

    private void doChatStream(
            Long tenantId,
            Long userId,
            String dataScope,
            MarketingAssistantChatRequest request,
            SseEmitter emitter) {
        AgentRuntimeRequest runtimeRequest = null;
        MarketingAssistantChatRequest safeRequest = request == null ? new MarketingAssistantChatRequest() : request;
        String scenario = resolveScenario(safeRequest);
        MarketingAssistantChatResponse fallback = null;
        try {
            sendThought(emitter, "已读取当前业务数据");
            fallback = ruleAssistant(tenantId, userId, dataScope, safeRequest, scenario);
            AgentEntity agent = resolveAgent(tenantId, fallback);
            if (agent == null && fallback.getMessage() != null) {
                sendDone(emitter, fallback);
                emitter.complete();
                return;
            }
            if (!canRunAgent(agent)) {
                fallback.setAvailable(false);
                fallback.setSuccess(true);
                fallback.setMessage("未配置通用营销助手智能体，已返回真实数据规则建议");
                sendThought(emitter, "未找到可用智能体配置，返回规则建议");
                sendDone(emitter, fallback);
                emitter.complete();
                return;
            }
            sendThought(emitter, "已准备营销助手回答策略");
            runtimeRequest = buildRuntimeRequest(tenantId, userId, dataScope, safeRequest, scenario, fallback, agent);
            String output = cleanAssistantOutput(collectRuntimeStream(emitter, runtimeRequest));
            MarketingAssistantChatResponse response = baseResponse(scenario, fallback.getTitle());
            copyAssistantState(response, fallback);
            response.setAvailable(true);
            response.setRunId(runtimeRequest.getRunId());
            response.setConversationId(runtimeRequest.getConversationId());
            if (!StringUtils.hasText(output)) {
                response.setSuccess(false);
                response.setMessage("AI营销助手未返回内容，已返回真实数据规则建议");
                response.setReply(fallback.getReply());
            } else {
                response.setSuccess(true);
                response.setMessage("AI营销助手回复完成");
                response.setReply(output.trim());
            }
                sendThought(emitter, "已生成销售可执行建议");
            sendDone(emitter, response);
            emitter.complete();
        } catch (RuntimeException ex) {
            MarketingAssistantChatResponse response = fallback == null
                    ? baseResponse(scenario, "请求失败")
                    : fallback;
            response.setAvailable(runtimeRequest != null);
            response.setSuccess(false);
            response.setMessage("AI营销助手调用失败，已返回真实数据规则建议：" + ex.getMessage());
            if (runtimeRequest != null) {
                response.setRunId(runtimeRequest.getRunId());
                response.setConversationId(runtimeRequest.getConversationId());
            }
            try {
                sendThought(emitter, "AI回复失败，已返回规则建议");
                sendDone(emitter, response);
                emitter.complete();
            } catch (RuntimeException sendEx) {
                emitter.completeWithError(sendEx);
            }
        }
    }

    private String collectRuntimeStream(SseEmitter emitter, AgentRuntimeRequest runtimeRequest) {
        final StringBuilder streamContent = new StringBuilder();
        final String[] finalContent = new String[1];
        agentRuntimeFacade
                .run(runtimeRequest)
                .doOnNext(event -> handleRuntimeEvent(emitter, event, streamContent, finalContent))
                .blockLast(Duration.ofSeconds(90));
        if (StringUtils.hasText(finalContent[0])) {
            return finalContent[0];
        }
        return streamContent.toString();
    }

    private void handleRuntimeEvent(
            SseEmitter emitter,
            AgentRuntimeEvent event,
            StringBuilder streamContent,
            String[] finalContent) {
        if (event == null) {
            return;
        }
        String type = event.getType() == null ? "" : event.getType().toUpperCase();
        if (type.contains("TOOL_CALL")) {
            sendThought(emitter, resolveToolThought(event));
            return;
        }
        if (type.contains("TOOL_RESULT")) {
            if (type.contains("END")) {
                sendThought(emitter, "已完成资料摘要");
            }
            return;
        }
        String content = event.getContent();
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (type.contains("RESULT") || type.contains("FINAL")) {
            finalContent[0] = content;
            return;
        }
        streamContent.append(content);
    }

    private String cleanAssistantOutput(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String text = output.trim();
        if (looksLikeRawKnowledgeResult(text)) {
            return "";
        }
        text = removeAssistantProcessText(text);
        return normalizeAssistantMarkdown(text);
    }

    private boolean looksLikeRawKnowledgeResult(String text) {
        String value = text == null ? "" : text.toLowerCase();
        return (value.contains("\"references\"") && value.contains("\"retrieval\""))
                || (value.contains("\"hitcount\"") && value.contains("\"query\""));
    }

    private String removeAssistantProcessText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        value = value.replaceFirst(
                "^(好的|好|收到|可以)[，,。\\s]*(我)?(先|再)?(查一下|查询一下|检索一下|看一下)[^。！？!?\n]*[。！？!?\\s]*",
                "");
        value = value.replaceFirst(
                "^(我)?(先|再)?(查一下|查询一下|检索一下|看一下)(知识库)?[^。！？!?\n]*[。！？!?\\s]*",
                "");
        value = value.replaceFirst(
                "^(让我)?(先|再)?(查一份|查一下|检索一下|看一下)更详细的?资料[^。！？!?\n]*[。！？!?\\s]*",
                "");
        value = value.replaceFirst(
                "^(好的|好)[，,。\\s]*以下是(知识库中)?关于[^\\n]{0,60}(完整介绍|详细介绍|资料)[：:\\s-]*",
                "");
        return value.trim();
    }

    private String normalizeAssistantMarkdown(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        value = value.replaceAll("([^\\n])\\s+---\\s*", "$1\n\n---\n\n");
        value = value.replaceAll("([^\\n|])(\\|[^\\n|]+\\|[^\\n|]+\\|)", "$1\n\n$2");
        value = value.replaceAll("(\\|[^\\n|]*\\|[^\\n|]*\\|)\\s+(?=\\|[^\\n|]*\\|[^\\n|]*\\|)", "$1\n");
        value = value.replaceAll("\\n{3,}", "\n\n");
        return value.trim();
    }

    private String resolveToolThought(AgentRuntimeEvent event) {
        String toolName = event == null ? "" : text(event.getToolName());
        if ("knowledge_search".equals(toolName)) {
            return "正在检索公司知识库";
        }
        if ("customer_web_search".equals(toolName)) {
            return "正在检索客户公开信息";
        }
        return "";
    }

    private void sendThought(SseEmitter emitter, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("type", "thought");
        payload.put("content", content);
        sendSse(emitter, "thought", payload);
    }

    private void sendDone(SseEmitter emitter, MarketingAssistantChatResponse response) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("type", "done");
        payload.put("response", response);
        sendSse(emitter, "done", payload);
    }

    private void sendSse(SseEmitter emitter, String eventName, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(Jsons.toJson(payload)));
        } catch (IOException ex) {
            throw new IllegalStateException("助手消息推送失败", ex);
        }
    }

    private AgentEntity resolveAgent(Long tenantId, MarketingAssistantChatResponse fallback) {
        try {
            return agentDefinitionService.findEnabledByScene(tenantId, GENERAL_ASSISTANT_SCENE);
        } catch (RuntimeException ex) {
            fallback.setAvailable(false);
            fallback.setSuccess(false);
            fallback.setMessage("通用营销助手智能体配置异常，已返回真实数据规则建议：" + ex.getMessage());
            return null;
        }
    }

    private MarketingAssistantChatResponse ruleAssistant(
            Long tenantId,
            Long userId,
            String dataScope,
            MarketingAssistantChatRequest request,
            String scenario) {
        if ("LEAD".equals(scenario)) {
            return leadAssistant(tenantId, userId, dataScope, request);
        }
        if ("CHANNEL".equals(scenario)) {
            return channelAssistant(tenantId, userId, dataScope, request);
        }
        if ("CUSTOMER".equals(scenario)) {
            return customerAssistant(tenantId, userId, dataScope);
        }
        if ("OPPORTUNITY".equals(scenario)) {
            return opportunityAssistant(tenantId, userId, dataScope);
        }
        return dashboardAssistant(tenantId, userId, dataScope);
    }

    private boolean canRunAgent(AgentEntity agent) {
        return agent != null && StringUtils.hasText(agent.getApiKey());
    }

    private AgentRuntimeRequest buildRuntimeRequest(
            Long tenantId,
            Long userId,
            String dataScope,
            MarketingAssistantChatRequest request,
            String scenario,
            MarketingAssistantChatResponse fallback,
            AgentEntity agent) {
        AgentRuntimeRequest runtimeRequest = new AgentRuntimeRequest();
        runtimeRequest.setTenantId(tenantId);
        runtimeRequest.setUserId(userId);
        runtimeRequest.setAgent(agent);
        runtimeRequest.setSessionId("marketing-assistant-" + userId);
        runtimeRequest.setSceneCode(GENERAL_ASSISTANT_SCENE);
        runtimeRequest.setBusinessType(resolveRuntimeBusinessType(request, scenario));
        runtimeRequest.setBusinessId(request.getBusinessId());
        runtimeRequest.setInjectedPrompt(buildMarketingAssistantPrompt());
        runtimeRequest.setMessage(buildMarketingAssistantMessage(request, scenario, dataScope, fallback));
        runtimeRequest.setContext(buildRuntimeContext(request, scenario, dataScope, fallback));
        return runtimeRequest;
    }

    private MarketingAssistantChatResponse buildAiResponse(
            String scenario,
            MarketingAssistantChatResponse fallback,
            AgentRuntimeRequest runtimeRequest,
            List<AgentRuntimeEvent> events) {
        MarketingAssistantChatResponse response = baseResponse(scenario, fallback.getTitle());
        copyAssistantState(response, fallback);
        response.setAvailable(true);
        response.setRunId(runtimeRequest.getRunId());
        response.setConversationId(runtimeRequest.getConversationId());
        String output = cleanAssistantOutput(resolveOutput(events));
        if (!StringUtils.hasText(output)) {
            response.setSuccess(false);
            response.setMessage("AI营销助手未返回内容，已返回真实数据规则建议");
            response.setReply(fallback.getReply());
            return response;
        }
        response.setSuccess(true);
        response.setMessage("AI营销助手回复完成");
        response.setReply(output.trim());
        return response;
    }

    private void copyAssistantState(MarketingAssistantChatResponse target, MarketingAssistantChatResponse source) {
        target.setReply(source.getReply());
        target.getSuggestions().addAll(source.getSuggestions());
        target.getQuickActions().addAll(source.getQuickActions());
        target.getMetrics().putAll(source.getMetrics());
    }

    private String buildMarketingAssistantPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("你是智能营销管理系统里的AI营销客服助手。");
        builder.append("你只能基于本次传入的真实业务摘要、对话历史和工具返回结果回答。");
        builder.append("禁止编造不存在的客户、线索、渠道、商机、产品能力、价格、案例或联系方式。");
        builder.append("当用户询问产品定位、解决方案、客户案例、FAQ、销售话术、投放文案时，");
        builder.append("必须先调用knowledge_search检索公司知识库。");
        builder.append("如果knowledge_search没有命中，必须明确说明知识库暂无资料，并给出需要补充的资料清单。");
        builder.append("知识库返回内容只能作为摘要参考，禁止把检索JSON、字段名和来源明细原样输出。");
        builder.append("不要向用户展示AgentRuntime、function call、tool、节点、Python、Java等底层实现字样。");
        builder.append("最终回复不要描述查询过程，不要输出“我查一下”“让我再查一份资料”等过程话。");
        builder.append("Markdown必须规范：标题使用###，段落之间留空行，列表每项独占一行。");
        builder.append("表格每一行必须完整写在同一行，内容太长时不要用表格，改用要点列表。");
        builder.append("生成营销话术、跟进场景、销售动作时禁止使用Markdown表格，必须使用“### 场景标题 + 适用场景 + 话术”格式。");
        builder.append("话术正文必须放在引用块或普通段落中，不要拆成场景、适用、话术三列表格。");
        builder.append("可以根据内容选择结论卡片、要点列表、对比表、行动清单等展示样式。");
        builder.append("不要把一个词、一句话拆成多行输出。");
        builder.append("用中文Markdown输出，语气像客服助手，先给结论，再给依据和下一步动作。");
        builder.append("回答尽量短，重点要能让销售直接执行。");
        return builder.toString();
    }

    private String buildMarketingAssistantMessage(
            MarketingAssistantChatRequest request,
            String scenario,
            String dataScope,
            MarketingAssistantChatResponse fallback) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("用户问题", text(request.getMessage()));
        data.put("当前场景", scenario);
        data.put("当前页面", text(request.getRouteKey()));
        data.put("数据权限", text(dataScope));
        data.put("业务类型", text(request.getBusinessType()));
        data.put("业务编号", text(request.getBusinessId()));
        data.put("真实指标", fallback.getMetrics());
        data.put("真实业务摘要", fallback.getReply());
        data.put("最近对话", resolveHistory(request));
        StringBuilder builder = new StringBuilder();
        builder.append("请像客服助手一样回答用户问题，必要时结合公司知识库。");
        builder.append("\n如果问题只涉及当前CRM数据，直接基于真实业务摘要回答。");
        builder.append("\n如果问题涉及产品、方案、案例、FAQ或话术，先调用knowledge_search。");
        builder.append("\n输入数据：").append(Jsons.toJson(data));
        return builder.toString();
    }

    private Map<String, Object> buildRuntimeContext(
            MarketingAssistantChatRequest request,
            String scenario,
            String dataScope,
            MarketingAssistantChatResponse fallback) {
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("scenario", scenario);
        context.put("routeKey", request.getRouteKey());
        context.put("businessType", request.getBusinessType());
        context.put("businessId", request.getBusinessId());
        context.put("dataScope", dataScope);
        context.put("metrics", fallback.getMetrics());
        return context;
    }

    private Object resolveHistory(MarketingAssistantChatRequest request) {
        Map<String, Object> context = request.getContext();
        if (context == null) {
            return new ArrayList<Object>();
        }
        Object history = context.get("history");
        return history == null ? new ArrayList<Object>() : history;
    }

    private String resolveRuntimeBusinessType(MarketingAssistantChatRequest request, String scenario) {
        if (StringUtils.hasText(request.getBusinessType())) {
            return request.getBusinessType().trim();
        }
        return scenario;
    }

    private String resolveOutput(List<AgentRuntimeEvent> events) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        String finalContent = null;
        StringBuilder streamContent = new StringBuilder();
        for (AgentRuntimeEvent event : events) {
            if (event == null || !StringUtils.hasText(event.getContent())) {
                continue;
            }
            String type = event.getType() == null ? "" : event.getType().toLowerCase();
            if (type.contains("tool_result")) {
                continue;
            }
            if (type.contains("result") || type.contains("final")) {
                finalContent = event.getContent();
            } else {
                streamContent.append(event.getContent());
            }
        }
        if (StringUtils.hasText(finalContent)) {
            return finalContent;
        }
        return streamContent.toString();
    }

    private MarketingAssistantChatResponse dashboardAssistant(Long tenantId, Long userId, String dataScope) {
        long leadCount = countLead(tenantId, userId, dataScope, null);
        long customerCount = countCustomer(tenantId, userId, dataScope, null);
        long opportunityCount = countOpportunity(tenantId, userId, dataScope, null);
        long channelCount = countChannel(tenantId, userId, dataScope, null);
        long newLeadCount = countLead(tenantId, userId, dataScope, LeadStatus.NEW);
        long followingLeadCount = countLead(tenantId, userId, dataScope, LeadStatus.FOLLOWING);
        long qualifiedLeadCount = countLead(tenantId, userId, dataScope, LeadStatus.QUALIFIED);
        long duplicateLeadCount = countLead(tenantId, userId, dataScope, LeadStatus.DUPLICATE);
        long closedLeadCount = countLead(tenantId, userId, dataScope, LeadStatus.CLOSED);
        BigDecimal opportunityAmount = sumOpportunityAmount(tenantId, userId, dataScope, null);
        BigDecimal wonAmount = sumOpportunityAmount(tenantId, userId, dataScope, OpportunityStage.WON);
        List<String> advice = new ArrayList<String>();
        if (leadCount + customerCount + opportunityCount + channelCount == 0) {
            advice.add("当前权限范围内暂未查询到业务数据，建议先从渠道管理创建获客表单或录入第一批线索。");
        } else {
            if (newLeadCount > 0) {
                advice.add("优先处理新线索 " + newLeadCount + " 条，先完成首次联系和基础信息补全。");
            }
            if (followingLeadCount + qualifiedLeadCount > 0) {
                advice.add("跟进中和有效线索合计 " + (followingLeadCount + qualifiedLeadCount)
                        + " 条，建议按成交可能性排序推进。");
            }
            if (duplicateLeadCount + closedLeadCount > 0) {
                advice.add("重复和已关闭线索合计 " + (duplicateLeadCount + closedLeadCount)
                        + " 条，建议定期清理，避免销售注意力被稀释。");
            }
            if (opportunityAmount.compareTo(BigDecimal.ZERO) > 0) {
                advice.add("当前商机金额 " + money(opportunityAmount) + "，已赢单金额 " + money(wonAmount)
                        + "，建议关注高金额未成交商机。");
            }
        }
        MarketingAssistantChatResponse response = baseResponse("DASHBOARD", "今日营销建议");
        response.setReply(buildDashboardReply(
                leadCount,
                customerCount,
                opportunityCount,
                channelCount,
                opportunityAmount,
                wonAmount,
                advice));
        response.getSuggestions().add("今天优先跟进什么？");
        response.getSuggestions().add("哪些线索需要转客户？");
        response.getSuggestions().add("渠道获客哪里可以优化？");
        response.getQuickActions().add(action("OPEN_LEADS", "查看线索", "进入线索列表处理待跟进数据", "leads"));
        response.getQuickActions().add(action("OPEN_CHANNELS", "查看渠道", "进入渠道管理查看获客来源", "channels"));
        response.getQuickActions().add(action("OPEN_OPPORTUNITIES", "查看商机", "进入商机管理推进成交", "opportunities"));
        putMetric(response, "leadCount", leadCount);
        putMetric(response, "customerCount", customerCount);
        putMetric(response, "opportunityCount", opportunityCount);
        putMetric(response, "channelCount", channelCount);
        putMetric(response, "opportunityAmount", opportunityAmount);
        putMetric(response, "wonAmount", wonAmount);
        return response;
    }

    private MarketingAssistantChatResponse leadAssistant(
            Long tenantId, Long userId, String dataScope, MarketingAssistantChatRequest request) {
        Long leadId = parseLong(request.getBusinessId());
        if (leadId != null) {
            return leadDetailAssistant(tenantId, userId, dataScope, leadId);
        }
        long newCount = countLead(tenantId, userId, dataScope, LeadStatus.NEW);
        long contactedCount = countLead(tenantId, userId, dataScope, LeadStatus.CONTACTED);
        long followingCount = countLead(tenantId, userId, dataScope, LeadStatus.FOLLOWING);
        long qualifiedCount = countLead(tenantId, userId, dataScope, LeadStatus.QUALIFIED);
        long convertedCount = countLead(tenantId, userId, dataScope, LeadStatus.CONVERTED);
        long invalidCount = countLead(tenantId, userId, dataScope, LeadStatus.INVALID);
        long duplicateCount = countLead(tenantId, userId, dataScope, LeadStatus.DUPLICATE);
        List<LeadEntity> recentLeads = recentLeads(tenantId, userId, dataScope);
        List<String> advice = new ArrayList<String>();
        if (newCount > 0) {
            advice.add("先处理新线索 " + newCount + " 条，目标是确认联系人、需求和下一次触达时间。");
        }
        if (qualifiedCount > 0) {
            advice.add("有效线索 " + qualifiedCount + " 条建议优先做 AI 分析，并准备转客户草稿。");
        }
        if (duplicateCount + invalidCount > 0) {
            advice.add("无效和重复线索合计 " + (duplicateCount + invalidCount) + " 条，建议清理或合并。");
        }
        if (advice.isEmpty()) {
            advice.add("当前线索压力不高，可以从渠道管理补充新的线索来源。");
        }
        MarketingAssistantChatResponse response = baseResponse("LEAD", "线索跟进建议");
        response.setReply(buildLeadReply(
                newCount,
                contactedCount,
                followingCount,
                qualifiedCount,
                convertedCount,
                invalidCount,
                duplicateCount,
                recentLeads,
                advice));
        response.getSuggestions().add("帮我判断哪些线索优先跟进");
        response.getSuggestions().add("线索转客户标准是什么？");
        response.getSuggestions().add("生成一段首次电话沟通话术");
        response.getQuickActions().add(action("OPEN_LEADS", "查看线索列表", "回到线索列表批量处理", "leads"));
        response.getQuickActions().add(action("OPEN_CHANNELS", "补充获客渠道", "去渠道管理创建新来源", "channels"));
        putMetric(response, "newLeadCount", newCount);
        putMetric(response, "qualifiedLeadCount", qualifiedCount);
        putMetric(response, "convertedLeadCount", convertedCount);
        return response;
    }

    private MarketingAssistantChatResponse leadDetailAssistant(
            Long tenantId, Long userId, String dataScope, Long leadId) {
        LeadEntity lead = findLead(tenantId, userId, dataScope, leadId);
        MarketingAssistantChatResponse response = baseResponse("LEAD", "线索详情建议");
        if (lead == null) {
            response.setReply("未查询到该线索，可能已删除或当前账号无数据权限。");
            response.getQuickActions().add(action("OPEN_LEADS", "返回线索列表", "查看当前可访问线索", "leads"));
            return response;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("### 线索当前判断\n\n");
        builder.append("- 名称：").append(text(lead.getName())).append("\n");
        builder.append("- 公司：").append(text(lead.getCompanyName())).append("\n");
        builder.append("- 状态：").append(leadStatusName(lead.getStatus())).append("\n");
        builder.append("- 来源：").append(text(lead.getSource())).append("\n");
        builder.append("- 手机：").append(text(lead.getPhone())).append("\n");
        builder.append("\n#### 建议动作\n\n");
        if (LeadStatus.CONVERTED == lead.getStatus()) {
            builder.append("- 该线索已转客户，后续重点应切到客户运营和商机推进。\n");
        } else if (LeadStatus.QUALIFIED == lead.getStatus()) {
            builder.append("- 当前是有效线索，可以运行线索 AI 分析后准备转客户。\n");
        } else if (LeadStatus.NEW == lead.getStatus()) {
            builder.append("- 当前是新线索，优先补齐联系人、需求、预算和下一步跟进时间。\n");
        } else {
            builder.append("- 建议先确认最近一次沟通结果，再决定继续跟进、培育或关闭。\n");
        }
        response.setReply(builder.toString());
        response.getSuggestions().add("分析这个线索能不能转客户");
        response.getSuggestions().add("帮我生成下一次跟进话术");
        response.getQuickActions().add(action("OPEN_LEADS", "查看线索列表", "回到线索列表继续处理", "leads"));
        response.getQuickActions().add(action("ANALYZE_LEAD", "运行线索分析", "使用线索 AI 分析生成结构化结论", "leads"));
        putMetric(response, "leadId", lead.getId());
        return response;
    }

    private MarketingAssistantChatResponse channelAssistant(
            Long tenantId, Long userId, String dataScope, MarketingAssistantChatRequest request) {
        long channelCount = countChannel(tenantId, userId, dataScope, null);
        long newCount = countChannel(tenantId, userId, dataScope, ChannelStatus.NEW);
        long waitingTranscriptCount = countChannel(tenantId, userId, dataScope, ChannelStatus.WAITING_TRANSCRIPTION);
        long waitingAiCount = countChannel(tenantId, userId, dataScope, ChannelStatus.WAITING_AI_ANALYSIS);
        long promotedCount = countChannel(tenantId, userId, dataScope, ChannelStatus.PROMOTED);
        long formCount = countMarketingForm(tenantId, userId, dataScope, null);
        long publishedFormCount = countMarketingForm(tenantId, userId, dataScope, MarketingFormStatus.PUBLISHED);
        List<ChannelRecordEntity> recentChannels = recentChannels(tenantId, userId, dataScope);
        MarketingAssistantChatResponse response = baseResponse("CHANNEL", "渠道获客建议");
        response.setReply(buildChannelReply(
                channelCount,
                newCount,
                waitingTranscriptCount,
                waitingAiCount,
                promotedCount,
                formCount,
                publishedFormCount,
                recentChannels,
                request.getMessage()));
        response.getSuggestions().add("帮我生成获客表单字段");
        response.getSuggestions().add("生成一条短信投放文案");
        response.getSuggestions().add("哪些渠道值得继续投放？");
        response.getQuickActions().add(action("OPEN_MARKETING_FORM_CREATE", "创建获客表单", "在渠道管理中创建公开表单", "channels"));
        response.getQuickActions().add(action("OPEN_CHANNELS", "查看渠道列表", "检查渠道记录和晋升情况", "channels"));
        putMetric(response, "channelCount", channelCount);
        putMetric(response, "formCount", formCount);
        putMetric(response, "publishedFormCount", publishedFormCount);
        return response;
    }

    private MarketingAssistantChatResponse customerAssistant(Long tenantId, Long userId, String dataScope) {
        long potentialCount = countCustomer(tenantId, userId, dataScope, CustomerStatus.POTENTIAL);
        long dealingCount = countCustomer(tenantId, userId, dataScope, CustomerStatus.DEALING);
        long cooperatedCount = countCustomer(tenantId, userId, dataScope, CustomerStatus.COOPERATED);
        long sleepingCount = countCustomer(tenantId, userId, dataScope, CustomerStatus.SLEEPING);
        List<CustomerEntity> recentCustomers = recentCustomers(tenantId, userId, dataScope);
        StringBuilder builder = new StringBuilder();
        builder.append("### 客户运营建议\n\n");
        builder.append("- 潜在客户：").append(potentialCount).append("\n");
        builder.append("- 商机推进客户：").append(dealingCount).append("\n");
        builder.append("- 已合作客户：").append(cooperatedCount).append("\n");
        builder.append("- 沉睡客户：").append(sleepingCount).append("\n");
        builder.append("\n#### 建议动作\n\n");
        if (dealingCount > 0) {
            builder.append("- 优先检查商机推进客户，确认是否已有明确商机和预计成交时间。\n");
        }
        if (sleepingCount > 0) {
            builder.append("- 对沉睡客户做一次召回分层，避免统一群发导致触达质量下降。\n");
        }
        if (potentialCount > 0) {
            builder.append("- 潜在客户需要补齐行业、联系人和最近沟通记录。\n");
        }
        appendRecentCustomers(builder, recentCustomers);
        MarketingAssistantChatResponse response = baseResponse("CUSTOMER", "客户运营建议");
        response.setReply(builder.toString());
        response.getSuggestions().add("哪些客户应该优先跟进？");
        response.getSuggestions().add("帮我做客户分层建议");
        response.getQuickActions().add(action("OPEN_CUSTOMERS", "查看客户列表", "进入客户管理处理客户数据", "customers"));
        putMetric(response, "potentialCustomerCount", potentialCount);
        putMetric(response, "dealingCustomerCount", dealingCount);
        putMetric(response, "cooperatedCustomerCount", cooperatedCount);
        return response;
    }

    private MarketingAssistantChatResponse opportunityAssistant(Long tenantId, Long userId, String dataScope) {
        long discoveryCount = countOpportunity(tenantId, userId, dataScope, OpportunityStage.DISCOVERY);
        long proposalCount = countOpportunity(tenantId, userId, dataScope, OpportunityStage.PROPOSAL);
        long negotiationCount = countOpportunity(tenantId, userId, dataScope, OpportunityStage.NEGOTIATION);
        long wonCount = countOpportunity(tenantId, userId, dataScope, OpportunityStage.WON);
        long lostCount = countOpportunity(tenantId, userId, dataScope, OpportunityStage.LOST);
        BigDecimal amount = sumOpportunityAmount(tenantId, userId, dataScope, null);
        BigDecimal wonAmount = sumOpportunityAmount(tenantId, userId, dataScope, OpportunityStage.WON);
        StringBuilder builder = new StringBuilder();
        builder.append("### 商机推进建议\n\n");
        builder.append("- 需求发现：").append(discoveryCount).append("\n");
        builder.append("- 方案报价：").append(proposalCount).append("\n");
        builder.append("- 商务谈判：").append(negotiationCount).append("\n");
        builder.append("- 已成交：").append(wonCount).append("\n");
        builder.append("- 已丢单：").append(lostCount).append("\n");
        builder.append("- 商机总金额：").append(money(amount)).append("\n");
        builder.append("- 已成交金额：").append(money(wonAmount)).append("\n");
        builder.append("\n#### 建议动作\n\n");
        if (negotiationCount > 0) {
            builder.append("- 商务谈判阶段需要优先推进，建议确认决策人、预算和签约阻碍。\n");
        }
        if (proposalCount > 0) {
            builder.append("- 方案报价阶段建议补齐客户痛点、竞品情况和价值证明。\n");
        }
        if (lostCount > 0) {
            builder.append("- 已丢单商机建议复盘原因，避免同类客户重复踩坑。\n");
        }
        MarketingAssistantChatResponse response = baseResponse("OPPORTUNITY", "商机推进建议");
        response.setReply(builder.toString());
        response.getSuggestions().add("哪些商机最应该今天推进？");
        response.getSuggestions().add("帮我生成谈判阶段跟进话术");
        response.getQuickActions().add(action("OPEN_OPPORTUNITIES", "查看商机列表", "进入商机管理推进成交", "opportunities"));
        putMetric(response, "opportunityAmount", amount);
        putMetric(response, "wonAmount", wonAmount);
        return response;
    }

    private String buildDashboardReply(
            long leadCount,
            long customerCount,
            long opportunityCount,
            long channelCount,
            BigDecimal opportunityAmount,
            BigDecimal wonAmount,
            List<String> advice) {
        StringBuilder builder = new StringBuilder();
        builder.append("### 今日营销概览\n\n");
        builder.append("- 线索：").append(leadCount).append("\n");
        builder.append("- 客户：").append(customerCount).append("\n");
        builder.append("- 商机：").append(opportunityCount).append("\n");
        builder.append("- 渠道记录：").append(channelCount).append("\n");
        builder.append("- 商机金额：").append(money(opportunityAmount)).append("\n");
        builder.append("- 已赢单金额：").append(money(wonAmount)).append("\n");
        builder.append("\n#### 优先建议\n\n");
        appendAdvice(builder, advice);
        return builder.toString();
    }

    private String buildLeadReply(
            long newCount,
            long contactedCount,
            long followingCount,
            long qualifiedCount,
            long convertedCount,
            long invalidCount,
            long duplicateCount,
            List<LeadEntity> recentLeads,
            List<String> advice) {
        StringBuilder builder = new StringBuilder();
        builder.append("### 线索池情况\n\n");
        builder.append("- 新线索：").append(newCount).append("\n");
        builder.append("- 已联系：").append(contactedCount).append("\n");
        builder.append("- 跟进中：").append(followingCount).append("\n");
        builder.append("- 有效线索：").append(qualifiedCount).append("\n");
        builder.append("- 已转化：").append(convertedCount).append("\n");
        builder.append("- 无效线索：").append(invalidCount).append("\n");
        builder.append("- 重复线索：").append(duplicateCount).append("\n");
        builder.append("\n#### 优先建议\n\n");
        appendAdvice(builder, advice);
        appendRecentLeads(builder, recentLeads);
        return builder.toString();
    }

    private String buildChannelReply(
            long channelCount,
            long newCount,
            long waitingTranscriptCount,
            long waitingAiCount,
            long promotedCount,
            long formCount,
            long publishedFormCount,
            List<ChannelRecordEntity> recentChannels,
            String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("### 渠道获客情况\n\n");
        builder.append("- 渠道记录：").append(channelCount).append("\n");
        builder.append("- 新渠道：").append(newCount).append("\n");
        builder.append("- 待转译：").append(waitingTranscriptCount).append("\n");
        builder.append("- 待AI分析：").append(waitingAiCount).append("\n");
        builder.append("- 已晋升线索：").append(promotedCount).append("\n");
        builder.append("- 获客表单：").append(formCount).append("\n");
        builder.append("- 已发布表单：").append(publishedFormCount).append("\n");
        builder.append("\n#### 建议动作\n\n");
        if (publishedFormCount == 0) {
            builder.append("- 当前没有已发布表单，建议先创建一个产品预约或资料领取表单。\n");
        }
        if (waitingTranscriptCount + waitingAiCount > 0) {
            builder.append("- 待转译和待AI分析记录合计 ")
                    .append(waitingTranscriptCount + waitingAiCount)
                    .append(" 条，建议先处理这批高信息量渠道。\n");
        }
        if (contains(message, "短信")) {
            builder.append("\n#### 短信文案草稿\n\n");
            builder.append("【请替换品牌】您好，我们准备了一份业务咨询表，填写后会由专人联系您确认需求：{表单短链}。");
            builder.append("如已提交可忽略，退订回T。\n");
        }
        if (contains(message, "表单")) {
            builder.append("\n#### 推荐表单字段\n\n");
            builder.append("- 姓名\n");
            builder.append("- 公司名称\n");
            builder.append("- 手机号\n");
            builder.append("- 需求描述\n");
            builder.append("- 预计采购时间\n");
        }
        appendRecentChannels(builder, recentChannels);
        return builder.toString();
    }

    private void appendAdvice(StringBuilder builder, List<String> advice) {
        if (advice == null || advice.isEmpty()) {
            builder.append("- 暂未发现必须立即处理的异常项。\n");
            return;
        }
        for (String item : advice) {
            builder.append("- ").append(item).append("\n");
        }
    }

    private void appendRecentLeads(StringBuilder builder, List<LeadEntity> leads) {
        builder.append("\n#### 最近线索\n\n");
        if (leads == null || leads.isEmpty()) {
            builder.append("暂无线索数据。\n");
            return;
        }
        for (LeadEntity lead : leads) {
            builder.append("- ")
                    .append(text(lead.getCompanyName(), lead.getName()))
                    .append(" · ")
                    .append(leadStatusName(lead.getStatus()))
                    .append(" · 来源 ")
                    .append(text(lead.getSource()))
                    .append("\n");
        }
    }

    private void appendRecentChannels(StringBuilder builder, List<ChannelRecordEntity> channels) {
        builder.append("\n#### 最近渠道记录\n\n");
        if (channels == null || channels.isEmpty()) {
            builder.append("暂无渠道记录。\n");
            return;
        }
        for (ChannelRecordEntity channel : channels) {
            builder.append("- ")
                    .append(text(channel.getTitle()))
                    .append(" · ")
                    .append(channelStatusName(channel.getStatus()))
                    .append(" · 来源 ")
                    .append(text(channel.getSource()))
                    .append("\n");
        }
    }

    private void appendRecentCustomers(StringBuilder builder, List<CustomerEntity> customers) {
        builder.append("\n#### 最近客户\n\n");
        if (customers == null || customers.isEmpty()) {
            builder.append("暂无客户数据。\n");
            return;
        }
        for (CustomerEntity customer : customers) {
            builder.append("- ")
                    .append(text(customer.getName()))
                    .append(" · ")
                    .append(customerStatusName(customer.getStatus()))
                    .append(" · 联系人 ")
                    .append(text(customer.getContactName()))
                    .append("\n");
        }
    }

    private MarketingAssistantChatResponse baseResponse(String scenario, String title) {
        MarketingAssistantChatResponse response = new MarketingAssistantChatResponse();
        response.setScenario(scenario);
        response.setTitle(title);
        return response;
    }

    private MarketingAssistantActionResponse action(String code, String title, String description, String targetRoute) {
        MarketingAssistantActionResponse action = new MarketingAssistantActionResponse();
        action.setCode(code);
        action.setTitle(title);
        action.setDescription(description);
        action.setTargetRoute(targetRoute);
        return action;
    }

    private void putMetric(MarketingAssistantChatResponse response, String key, Object value) {
        response.getMetrics().put(key, value);
    }

    private String resolveScenario(MarketingAssistantChatRequest request) {
        String businessType = upper(request.getBusinessType());
        String routeKey = lower(request.getRouteKey());
        String message = lower(request.getMessage());
        if ("LEAD".equals(businessType) || routeKey.startsWith("leads") || contains(message, "线索")) {
            return "LEAD";
        }
        if ("CHANNEL".equals(businessType)
                || routeKey.startsWith("channels")
                || contains(message, "渠道")
                || contains(message, "表单")
                || contains(message, "短信")
                || contains(message, "获客")) {
            return "CHANNEL";
        }
        if ("CUSTOMER".equals(businessType) || routeKey.startsWith("customers") || contains(message, "客户")) {
            return "CUSTOMER";
        }
        if ("OPPORTUNITY".equals(businessType)
                || routeKey.startsWith("opportunities")
                || contains(message, "商机")
                || contains(message, "成交")) {
            return "OPPORTUNITY";
        }
        return "DASHBOARD";
    }

    private long countLead(Long tenantId, Long userId, String dataScope, LeadStatus status) {
        QueryWrapper<LeadEntity> wrapper = leadBase(tenantId, userId, dataScope);
        if (status != null) {
            wrapper.eq("status", status.name());
        }
        return count(leadMapper.selectCount(wrapper));
    }

    private long countCustomer(Long tenantId, Long userId, String dataScope, CustomerStatus status) {
        QueryWrapper<CustomerEntity> wrapper = customerBase(tenantId, userId, dataScope);
        if (status != null) {
            wrapper.eq("status", status.name());
        }
        return count(customerMapper.selectCount(wrapper));
    }

    private long countOpportunity(Long tenantId, Long userId, String dataScope, OpportunityStage stage) {
        QueryWrapper<OpportunityEntity> wrapper = opportunityBase(tenantId, userId, dataScope);
        if (stage != null) {
            wrapper.eq("stage", stage.name());
        }
        return count(opportunityMapper.selectCount(wrapper));
    }

    private long countChannel(Long tenantId, Long userId, String dataScope, ChannelStatus status) {
        QueryWrapper<ChannelRecordEntity> wrapper = channelBase(tenantId, userId, dataScope);
        if (status != null) {
            wrapper.eq("status", status.name());
        }
        return count(channelRecordMapper.selectCount(wrapper));
    }

    private long countMarketingForm(Long tenantId, Long userId, String dataScope, MarketingFormStatus status) {
        QueryWrapper<MarketingFormEntity> wrapper = marketingFormBase(tenantId, userId, dataScope);
        if (status != null) {
            wrapper.eq("status", status.name());
        }
        return count(marketingFormMapper.selectCount(wrapper));
    }

    private BigDecimal sumOpportunityAmount(Long tenantId, Long userId, String dataScope, OpportunityStage stage) {
        QueryWrapper<OpportunityEntity> wrapper = opportunityBase(tenantId, userId, dataScope);
        if (stage != null) {
            wrapper.eq("stage", stage.name());
        }
        wrapper.select("coalesce(sum(amount), 0)");
        List<Object> values = opportunityMapper.selectObjs(wrapper);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return BigDecimal.ZERO;
        }
        Object value = values.get(0);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private List<LeadEntity> recentLeads(Long tenantId, Long userId, String dataScope) {
        return leadMapper.selectList(leadBase(tenantId, userId, dataScope).orderByDesc("created_at").last("limit 5"));
    }

    private List<ChannelRecordEntity> recentChannels(Long tenantId, Long userId, String dataScope) {
        return channelRecordMapper.selectList(
                channelBase(tenantId, userId, dataScope).orderByDesc("created_at").last("limit 5"));
    }

    private List<CustomerEntity> recentCustomers(Long tenantId, Long userId, String dataScope) {
        return customerMapper.selectList(
                customerBase(tenantId, userId, dataScope).orderByDesc("created_at").last("limit 5"));
    }

    private LeadEntity findLead(Long tenantId, Long userId, String dataScope, Long leadId) {
        QueryWrapper<LeadEntity> wrapper = leadBase(tenantId, userId, dataScope);
        wrapper.eq("id", leadId);
        return leadMapper.selectOne(wrapper);
    }

    private QueryWrapper<LeadEntity> leadBase(Long tenantId, Long userId, String dataScope) {
        QueryWrapper<LeadEntity> wrapper = new QueryWrapper<LeadEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, userId, dataScope);
        return wrapper;
    }

    private QueryWrapper<CustomerEntity> customerBase(Long tenantId, Long userId, String dataScope) {
        QueryWrapper<CustomerEntity> wrapper = new QueryWrapper<CustomerEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, userId, dataScope);
        return wrapper;
    }

    private QueryWrapper<OpportunityEntity> opportunityBase(Long tenantId, Long userId, String dataScope) {
        QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, userId, dataScope);
        return wrapper;
    }

    private QueryWrapper<ChannelRecordEntity> channelBase(Long tenantId, Long userId, String dataScope) {
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, userId, dataScope);
        return wrapper;
    }

    private QueryWrapper<MarketingFormEntity> marketingFormBase(Long tenantId, Long userId, String dataScope) {
        QueryWrapper<MarketingFormEntity> wrapper = new QueryWrapper<MarketingFormEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, userId, dataScope);
        return wrapper;
    }

    private void applyOwnerScope(QueryWrapper<?> wrapper, Long userId, String dataScope) {
        if ("SELF".equals(dataScope)) {
            wrapper.eq("owner_id", userId);
        }
    }

    private long count(Long value) {
        return value == null ? 0L : value.longValue();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean contains(String source, String keyword) {
        return source != null && keyword != null && source.contains(keyword);
    }

    private String text(String value) {
        if (!StringUtils.hasText(value)) {
            return "未填写";
        }
        return value.trim();
    }

    private String text(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return text(second);
    }

    private String money(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return amount.stripTrailingZeros().toPlainString();
    }

    private String leadStatusName(LeadStatus status) {
        if (status == null) {
            return "未填写";
        }
        Map<LeadStatus, String> names = new LinkedHashMap<LeadStatus, String>();
        names.put(LeadStatus.NEW, "新线索");
        names.put(LeadStatus.CONTACTED, "已联系");
        names.put(LeadStatus.FOLLOWING, "跟进中");
        names.put(LeadStatus.QUALIFIED, "有效线索");
        names.put(LeadStatus.NURTURING, "长期培育");
        names.put(LeadStatus.CONVERTED, "已转化");
        names.put(LeadStatus.INVALID, "无效线索");
        names.put(LeadStatus.DUPLICATE, "重复线索");
        names.put(LeadStatus.CLOSED, "已关闭");
        return names.get(status) == null ? status.name() : names.get(status);
    }

    private String customerStatusName(CustomerStatus status) {
        if (status == null) {
            return "未填写";
        }
        Map<CustomerStatus, String> names = new LinkedHashMap<CustomerStatus, String>();
        names.put(CustomerStatus.POTENTIAL, "潜在客户");
        names.put(CustomerStatus.ACTIVE, "正常经营");
        names.put(CustomerStatus.DEALING, "商机推进");
        names.put(CustomerStatus.COOPERATED, "已合作");
        names.put(CustomerStatus.SLEEPING, "沉睡客户");
        names.put(CustomerStatus.CHURNED, "已流失");
        names.put(CustomerStatus.BLACKLIST, "黑名单");
        return names.get(status) == null ? status.name() : names.get(status);
    }

    private String channelStatusName(ChannelStatus status) {
        if (status == null) {
            return "未填写";
        }
        Map<ChannelStatus, String> names = new LinkedHashMap<ChannelStatus, String>();
        names.put(ChannelStatus.NEW, "新渠道");
        names.put(ChannelStatus.WAITING_TRANSCRIPTION, "待转译");
        names.put(ChannelStatus.TRANSCRIBED, "已转译");
        names.put(ChannelStatus.WAITING_AI_ANALYSIS, "待AI分析");
        names.put(ChannelStatus.ANALYZED, "已分析");
        names.put(ChannelStatus.PROMOTED, "已晋升");
        return names.get(status) == null ? status.name() : names.get(status);
    }
}
