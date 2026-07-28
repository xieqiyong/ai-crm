package com.hz.crm.agent.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.service.AgentDefinitionService;
import com.hz.crm.agent.runtime.service.AgentRuntimeFacade;
import com.hz.crm.agent.web.dto.CustomerDeepSummaryRequest;
import com.hz.crm.agent.web.dto.CustomerDeepSummaryResponse;
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
            String output = resolveOutput(events);
            response.setAvailable(true);
            response.setSuccess(true);
            response.setMessage("客户 AI 深度总结完成");
            response.setRunId(runtimeRequest.getRunId());
            response.setConversationId(runtimeRequest.getConversationId());
            String summary = StringUtils.hasText(output)
                    ? output.trim()
                    : buildBasicSummary(customer, opportunities, opportunityProducts, followups);
            response.setSummary(summary);
            fillBasicLists(response, customer, opportunities, opportunityProducts, followups);
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
        builder.append("禁止编造不存在的联系人、预算、成交金额、沟通记录、风险或下一步动作。");
        builder.append("输出中文Markdown，结构必须包含：客户判断、关键事实、商机情况、跟进情况、风险提醒、下一步动作。");
        builder.append("如果数据不足，必须明确说明缺少哪些数据。");
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
        String finalContent = null;
        StringBuilder streamContent = new StringBuilder();
        for (AgentRuntimeEvent event : events) {
            if (event == null || !StringUtils.hasText(event.getContent())) {
                continue;
            }
            String type = event.getType() == null ? "" : event.getType().toLowerCase();
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
