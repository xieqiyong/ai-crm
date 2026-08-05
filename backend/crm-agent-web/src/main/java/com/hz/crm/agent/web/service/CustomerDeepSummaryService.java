package com.hz.crm.agent.web.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.service.AgentDefinitionService;
import com.hz.crm.agent.runtime.service.AgentRuntimeFacade;
import com.hz.crm.agent.web.dto.CustomerDeepSummaryRequest;
import com.hz.crm.agent.web.dto.CustomerDeepSummaryResponse;
import com.hz.crm.agent.web.tool.CustomerDeepSummaryResultTool;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.json.Jsons;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.followup.FollowupRecordEntity;
import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.followup.mapper.FollowupRecordMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.mapper.LeadMapper;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityProductEntity;
import com.hz.crm.domain.opportunity.mapper.OpportunityMapper;
import com.hz.crm.domain.opportunity.mapper.OpportunityProductMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerDeepSummaryService {

    private static final String CUSTOMER_DEEP_SUMMARY_SCENE = "CUSTOMER_DEEP_SUMMARY";

    private static final String CUSTOMER_RESULT_FUNCTION = "customer_deep_summary_result";

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private OpportunityMapper opportunityMapper;

    @Autowired
    private OpportunityProductMapper opportunityProductMapper;

    @Autowired
    private FollowupRecordMapper followupRecordMapper;

    @Autowired
    private LeadMapper leadMapper;

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Autowired
    private AgentRuntimeFacade agentRuntimeFacade;

    @Transactional
    public CustomerDeepSummaryResponse summarize(
            Long tenantId, Long userId, String dataScope, CustomerDeepSummaryRequest request) {
        if (request == null || request.getCustomerId() == null) {
            throw new BusinessException("CUSTOMER_AI_001", "客户编号不能为空");
        }
        CustomerEntity customer = findCustomer(tenantId, request.getCustomerId());
        checkDataScope(userId, dataScope, customer.getOwnerId());
        List<OpportunityEntity> opportunities = queryOpportunities(tenantId, userId, dataScope, customer.getId());
        List<OpportunityProductEntity> opportunityProducts = queryOpportunityProducts(tenantId, opportunities);
        List<FollowupRecordEntity> followups = queryFollowups(tenantId, userId, dataScope, customer.getId());
        CustomerDeepSummaryResponse response = baseResponse(customer, opportunities, followups);
        AgentEntity agent = agentDefinitionService.findEnabledByScene(tenantId, CUSTOMER_DEEP_SUMMARY_SCENE);
        if (agent == null || !StringUtils.hasText(agent.getApiKey())) {
            response.setAvailable(false);
            response.setSuccess(true);
            response.setMessage("未配置客户深度总结智能体，已返回真实数据基础总结");
            response.setSummary(buildBasicSummary(customer, opportunities, opportunityProducts, followups));
            fillBasicLists(response, customer, opportunities, opportunityProducts, followups);
            saveSummary(customer, response.getSummary());
            return response;
        }
        AgentRuntimeRequest runtimeRequest = null;
        try {
            runtimeRequest = buildRuntimeRequest(
                    tenantId, userId, customer, opportunities, opportunityProducts, followups, agent, request);
            List<AgentRuntimeEvent> events = agentRuntimeFacade
                    .run(runtimeRequest)
                    .collectList()
                    .block(Duration.ofSeconds(90));
            JSONObject result = resolveCapturedResult(runtimeRequest);
            if (result == null && !hasToolCall(events, CUSTOMER_RESULT_FUNCTION)) {
                throw new BusinessException("CUSTOMER_AI_002", "客户深度总结智能体未调用标准结果函数，请检查场景提示词");
            }
            if (result == null) {
                result = parseResult(resolveOutput(events));
            }
            if (result == null) {
                throw new BusinessException("CUSTOMER_AI_003", "客户深度总结智能体已调用结果函数，但函数参数无法解析");
            }
            response.setAvailable(true);
            response.setSuccess(true);
            response.setMessage("客户 AI 深度总结完成");
            response.setRunId(runtimeRequest.getRunId());
            response.setConversationId(runtimeRequest.getConversationId());
            fillStructuredResult(response, result);
            saveSummary(customer, response.getSummary());
            return response;
        } catch (RuntimeException ex) {
            response.setAvailable(true);
            response.setSuccess(false);
            response.setMessage("客户 AI 深度总结失败：" + ex.getMessage());
            if (runtimeRequest != null) {
                response.setRunId(runtimeRequest.getRunId());
                response.setConversationId(runtimeRequest.getConversationId());
            }
            return response;
        }
    }

    private CustomerDeepSummaryResponse baseResponse(
            CustomerEntity customer, List<OpportunityEntity> opportunities, List<FollowupRecordEntity> followups) {
        CustomerDeepSummaryResponse response = new CustomerDeepSummaryResponse();
        response.setCustomerId(customer.getId());
        response.setOpportunityCount(opportunities == null ? 0 : opportunities.size());
        response.setFollowupCount(followups == null ? 0 : followups.size());
        response.setAnalyzedAt(DateTimes.now());
        return response;
    }

    private AgentRuntimeRequest buildRuntimeRequest(
            Long tenantId,
            Long userId,
            CustomerEntity customer,
            List<OpportunityEntity> opportunities,
            List<OpportunityProductEntity> opportunityProducts,
            List<FollowupRecordEntity> followups,
            AgentEntity agent,
            CustomerDeepSummaryRequest request) {
        AgentRuntimeRequest runtimeRequest = new AgentRuntimeRequest();
        runtimeRequest.setTenantId(tenantId);
        runtimeRequest.setUserId(userId);
        runtimeRequest.setAgent(agent);
        runtimeRequest.setSessionId("customer-summary-" + customer.getId());
        runtimeRequest.setSceneCode(CUSTOMER_DEEP_SUMMARY_SCENE);
        runtimeRequest.setBusinessType("CUSTOMER");
        runtimeRequest.setBusinessId(String.valueOf(customer.getId()));
        runtimeRequest.setInjectedPrompt(buildInjectedPrompt());
        runtimeRequest.setMessage(buildMessage(
                customer, opportunities, opportunityProducts, followups, request.getInstruction()));
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("businessType", "CUSTOMER");
        context.put("customerId", String.valueOf(customer.getId()));
        runtimeRequest.setContext(context);
        return runtimeRequest;
    }

    private String buildInjectedPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("只能基于本次传入的客户真实数据、商机真实数据、跟进记录真实数据总结。");
        builder.append("跟进记录中targetType为LEAD的数据表示客户转化前的原线索阶段跟进。");
        builder.append("商机产品明细表示客户可能购买的产品或服务，需要用于判断销售重点。");
        builder.append("判断产品定位、产品匹配、解决方案或销售话术时必须先调用knowledge_search，未命中时必须说明知识库暂无资料。");
        builder.append("存在公司名称时可调用customer_web_search补充公开企业信息，未确认字段不能猜测。");
        builder.append("禁止编造不存在的联系人、预算、成交金额、沟通记录、风险或下一步动作。");
        builder.append("最终动作必须且只能调用一次customer_deep_summary_result，字段必须直接作为函数参数传入。");
        builder.append("禁止把函数参数包装成字符串，禁止输出customer_deep_summary_result(...)文本。");
        builder.append("函数调用成功后立即结束，禁止继续输出解释、Markdown、代码块或自然语言。");
        return builder.toString();
    }

    private String buildMessage(
            CustomerEntity customer,
            List<OpportunityEntity> opportunities,
            List<OpportunityProductEntity> opportunityProducts,
            List<FollowupRecordEntity> followups,
            String instruction) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("customer", customer);
        data.put("opportunities", opportunities);
        data.put("opportunityProducts", opportunityProducts);
        data.put("followups", followups);
        StringBuilder builder = new StringBuilder();
        builder.append("请对以下客户做深度总结，给销售负责人可执行的建议。");
        builder.append("必须覆盖客户判断、关键事实、商机洞察、跟进洞察、风险提醒、下一步动作和缺失信息。");
        builder.append("最终直接调用customer_deep_summary_result并传入结构化参数，不要用文本模拟函数调用。");
        if (StringUtils.hasText(instruction)) {
            builder.append("\n补充要求：").append(instruction.trim());
        }
        builder.append("\n真实数据：").append(Jsons.toJson(data));
        return builder.toString();
    }

    private String resolveOutput(List<AgentRuntimeEvent> events) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        StringBuilder toolResult = new StringBuilder();
        String customerToolResult = null;
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
                if (CUSTOMER_RESULT_FUNCTION.equals(event.getToolName())) {
                    customerToolResult = toolResult.toString();
                }
                toolResult.setLength(0);
            }
        }
        if (StringUtils.hasText(customerToolResult)) {
            return customerToolResult;
        }
        for (int index = events.size() - 1; index >= 0; index--) {
            AgentRuntimeEvent event = events.get(index);
            if (event != null
                    && StringUtils.hasText(event.getContent())
                    && (CUSTOMER_RESULT_FUNCTION.equals(event.getToolName())
                    || event.getContent().contains(CUSTOMER_RESULT_FUNCTION))) {
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

    private JSONObject resolveCapturedResult(AgentRuntimeRequest runtimeRequest) {
        if (runtimeRequest == null || runtimeRequest.getContext() == null) {
            return null;
        }
        Object value = runtimeRequest.getContext().remove(CustomerDeepSummaryResultTool.RESULT_CONTEXT_KEY);
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

    private JSONObject parseResult(String output) {
        if (!StringUtils.hasText(output)) {
            return null;
        }
        String text = output.trim();
        JSONObject direct = parseJsonObject(text);
        if (direct != null) {
            return direct;
        }
        int functionIndex = text.indexOf(CUSTOMER_RESULT_FUNCTION);
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

    private void fillStructuredResult(CustomerDeepSummaryResponse response, JSONObject result) {
        response.setKeyFindings(resolveStringList(result.getJSONArray("keyFindings"), 6, 120));
        response.setRisks(resolveStringList(result.getJSONArray("riskWarnings"), 5, 120));
        response.setNextActions(resolveStringList(result.getJSONArray("nextActions"), 5, 120));
        response.setSummary(buildStructuredSummary(result));
    }

    private String buildStructuredSummary(JSONObject result) {
        StringBuilder builder = new StringBuilder();
        builder.append("### ").append(resolveText(result.getString("conclusionTitle"), "客户深度总结")).append("\n\n");
        builder.append(resolveText(result.getString("summary"), "暂无概要")).append("\n\n");
        appendTextSection(builder, "客户判断", result.getString("customerJudgement"));
        appendListSection(builder, "关键事实", result.getJSONArray("keyFindings"), 6);
        appendListSection(builder, "商机洞察", result.getJSONArray("opportunityInsights"), 5);
        appendListSection(builder, "跟进洞察", result.getJSONArray("followupInsights"), 5);
        appendListSection(builder, "风险提醒", result.getJSONArray("riskWarnings"), 5);
        appendListSection(builder, "下一步动作", result.getJSONArray("nextActions"), 5);
        appendListSection(builder, "缺失信息", result.getJSONArray("missingData"), 5);
        return builder.toString().trim();
    }

    private void appendTextSection(StringBuilder builder, String title, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        builder.append("#### ").append(title).append("\n\n");
        builder.append(value.trim()).append("\n\n");
    }

    private void appendListSection(StringBuilder builder, String title, JSONArray values, int maxSize) {
        List<String> list = resolveStringList(values, maxSize, 160);
        if (list.isEmpty()) {
            return;
        }
        builder.append("#### ").append(title).append("\n\n");
        for (String value : list) {
            builder.append("- ").append(value).append("\n");
        }
        builder.append("\n");
    }

    private List<String> resolveStringList(JSONArray source, int maxSize, int maxLength) {
        List<String> values = new ArrayList<String>();
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

    private String resolveText(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return fallback;
    }

    private String buildBasicSummary(
            CustomerEntity customer,
            List<OpportunityEntity> opportunities,
            List<OpportunityProductEntity> opportunityProducts,
            List<FollowupRecordEntity> followups) {
        StringBuilder builder = new StringBuilder();
        builder.append("### 客户基础总结\n\n");
        builder.append("- 客户名称：").append(text(customer.getName())).append("\n");
        builder.append("- 行业：").append(text(customer.getIndustry())).append("\n");
        builder.append("- 主联系人：").append(text(customer.getContactName())).append("\n");
        builder.append("- 联系电话：").append(text(customer.getContactPhone())).append("\n");
        String status = customer.getStatus() == null ? "未填写" : customer.getStatus().name();
        builder.append("- 客户状态：").append(status).append("\n");
        builder.append("- 关联商机数：").append(opportunities == null ? 0 : opportunities.size()).append("\n");
        builder.append("- 商机产品明细数：");
        builder.append(opportunityProducts == null ? 0 : opportunityProducts.size()).append("\n");
        if (opportunityProducts != null && !opportunityProducts.isEmpty()) {
            builder.append("- 关联产品：").append(joinProductNames(opportunityProducts)).append("\n");
        }
        builder.append("- 跟进记录数：").append(followups == null ? 0 : followups.size()).append("\n");
        builder.append("\n#### 当前判断\n\n");
        if (opportunities != null && !opportunities.isEmpty()) {
            builder.append("- 当前客户已经关联商机，建议围绕商机阶段推进下一步销售动作。\n");
        } else {
            builder.append("- 当前客户暂无关联商机，建议先确认需求和预算，再创建商机。\n");
        }
        if (followups != null && !followups.isEmpty()) {
            builder.append("- 已有真实跟进记录，可结合最近一次跟进结果安排下一步。\n");
        } else {
            builder.append("- 暂无跟进记录，建议先补充一次电话、微信或会议跟进。\n");
        }
        return builder.toString();
    }

    private void fillBasicLists(
            CustomerDeepSummaryResponse response,
            CustomerEntity customer,
            List<OpportunityEntity> opportunities,
            List<OpportunityProductEntity> opportunityProducts,
            List<FollowupRecordEntity> followups) {
        response.getKeyFindings().add("客户：" + text(customer.getName()));
        if (StringUtils.hasText(customer.getContactName())) {
            response.getKeyFindings().add("主联系人：" + customer.getContactName());
        }
        response.getKeyFindings().add("关联商机数：" + (opportunities == null ? 0 : opportunities.size()));
        response.getKeyFindings().add("商机产品明细数："
                + (opportunityProducts == null ? 0 : opportunityProducts.size()));
        response.getKeyFindings().add("跟进记录数：" + (followups == null ? 0 : followups.size()));
        if (opportunityProducts != null && !opportunityProducts.isEmpty()) {
            response.getNextActions().add("围绕已关联产品推进报价、试用或方案确认");
        }
        if (!StringUtils.hasText(customer.getContactPhone()) && !StringUtils.hasText(customer.getContactEmail())) {
            response.getRisks().add("客户缺少电话和邮箱，后续触达风险较高");
        }
        if (opportunities == null || opportunities.isEmpty()) {
            response.getNextActions().add("确认客户需求和预算，满足条件后创建商机");
        } else {
            response.getNextActions().add("检查关联商机阶段，推进下一次销售动作");
        }
        if (followups == null || followups.isEmpty()) {
            response.getNextActions().add("补充一次真实跟进记录，形成可追踪时间轴");
        }
    }

    private void saveSummary(CustomerEntity customer, String summary) {
        customer.setAiSummary(summary);
        customer.setAiAnalyzedAt(DateTimes.now());
        customer.setUpdatedAt(DateTimes.now());
        customerMapper.updateById(customer);
    }

    private CustomerEntity findCustomer(Long tenantId, Long customerId) {
        QueryWrapper<CustomerEntity> wrapper = new QueryWrapper<CustomerEntity>();
        wrapper.eq("id", customerId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        CustomerEntity entity = customerMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("CUSTOMER_002", "客户不存在");
        }
        return entity;
    }

    private List<OpportunityEntity> queryOpportunities(Long tenantId, Long userId, String dataScope, Long customerId) {
        QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.eq("customer_id", customerId);
        if ("SELF".equals(dataScope)) {
            wrapper.eq("owner_id", userId);
        }
        wrapper.orderByDesc("created_at").last("limit 20");
        return opportunityMapper.selectList(wrapper);
    }

    private List<OpportunityProductEntity> queryOpportunityProducts(
            Long tenantId, List<OpportunityEntity> opportunities) {
        List<OpportunityProductEntity> emptyList = new ArrayList<OpportunityProductEntity>();
        if (opportunities == null || opportunities.isEmpty()) {
            return emptyList;
        }
        List<Long> opportunityIds = new ArrayList<Long>();
        for (OpportunityEntity opportunity : opportunities) {
            if (opportunity != null && opportunity.getId() != null) {
                opportunityIds.add(opportunity.getId());
            }
        }
        if (opportunityIds.isEmpty()) {
            return emptyList;
        }
        QueryWrapper<OpportunityProductEntity> wrapper = new QueryWrapper<OpportunityProductEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.in("opportunity_id", opportunityIds);
        wrapper.orderByAsc("created_at");
        return opportunityProductMapper.selectList(wrapper);
    }

    private List<FollowupRecordEntity> queryFollowups(Long tenantId, Long userId, String dataScope, Long customerId) {
        List<Long> relatedLeadIds = queryConvertedLeadIds(tenantId, customerId);
        QueryWrapper<FollowupRecordEntity> wrapper = new QueryWrapper<FollowupRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if (relatedLeadIds.isEmpty()) {
            wrapper.eq("target_type", FollowupTargetType.CUSTOMER.name());
            wrapper.eq("target_id", customerId);
        } else {
            wrapper.and(value -> value
                    .eq("target_type", FollowupTargetType.CUSTOMER.name())
                    .eq("target_id", customerId)
                    .or()
                    .eq("target_type", FollowupTargetType.LEAD.name())
                    .in("target_id", relatedLeadIds));
        }
        if ("SELF".equals(dataScope)) {
            wrapper.eq("owner_id", userId);
        }
        wrapper.orderByDesc("followup_at").last("limit 20");
        return followupRecordMapper.selectList(wrapper);
    }

    private List<Long> queryConvertedLeadIds(Long tenantId, Long customerId) {
        List<Long> leadIds = new ArrayList<Long>();
        QueryWrapper<LeadEntity> wrapper = new QueryWrapper<LeadEntity>();
        wrapper.select("id");
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.eq("customer_id", customerId);
        List<LeadEntity> leads = leadMapper.selectList(wrapper);
        for (LeadEntity lead : leads) {
            if (lead.getId() != null) {
                leadIds.add(lead.getId());
            }
        }
        return leadIds;
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    private String text(String value) {
        if (!StringUtils.hasText(value)) {
            return "未填写";
        }
        return value.trim();
    }

    private String shrink(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String joinProductNames(List<OpportunityProductEntity> products) {
        Set<String> names = new HashSet<String>();
        for (OpportunityProductEntity product : products) {
            if (product != null && StringUtils.hasText(product.getProductName())) {
                names.add(product.getProductName().trim());
            }
        }
        if (names.isEmpty()) {
            return "未填写";
        }
        return String.join("、", names);
    }
}
