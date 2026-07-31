package com.hz.crm.application.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.dashboard.dto.DashboardCountItem;
import com.hz.crm.application.dashboard.dto.DashboardOverviewResponse;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerStatus;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.followup.FollowupRecordEntity;
import com.hz.crm.domain.followup.mapper.FollowupRecordMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadStatus;
import com.hz.crm.domain.lead.mapper.LeadMapper;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityStage;
import com.hz.crm.domain.opportunity.mapper.OpportunityMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardApplicationService {

    @Autowired
    private LeadMapper leadMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private OpportunityMapper opportunityMapper;

    @Autowired
    private ChannelRecordMapper channelRecordMapper;

    @Autowired
    private FollowupRecordMapper followupRecordMapper;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(Long tenantId, Long userId, String dataScope) {
        Long ownerId = "SELF".equals(dataScope) ? userId : null;
        LocalDateTime todayStart = DateTimes.now().toLocalDate().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        DashboardOverviewResponse response = new DashboardOverviewResponse();
        response.setLeadCount(count(leadMapper.selectCount(leadBase(tenantId, ownerId))));
        response.setCustomerCount(count(customerMapper.selectCount(customerBase(tenantId, ownerId))));
        response.setOpportunityCount(count(opportunityMapper.selectCount(opportunityBase(tenantId, ownerId))));
        response.setChannelCount(count(channelRecordMapper.selectCount(channelBase(tenantId, ownerId))));
        response.setOpportunityAmount(sumOpportunityAmount(tenantId, ownerId, null));
        response.setWonAmount(sumOpportunityAmount(tenantId, ownerId, OpportunityStage.WON));
        response.setTodayFollowupCount(countTodayFollowup(tenantId, ownerId, todayStart, tomorrowStart));
        response.setTodayChannelUserCount(countTodayChannel(tenantId, ownerId, todayStart, tomorrowStart));
        response.setTodayLeadConversionCount(
                countTodayLeadConversion(tenantId, ownerId, todayStart, tomorrowStart));
        response.setTodayNewLeadCount(countTodayLead(tenantId, ownerId, todayStart, tomorrowStart));
        response.setLeadStatusCounts(buildLeadStatusCounts(tenantId, ownerId));
        response.setCustomerStatusCounts(buildCustomerStatusCounts(tenantId, ownerId));
        response.setOpportunityStageCounts(buildOpportunityStageCounts(tenantId, ownerId));
        response.setChannelStatusCounts(buildChannelStatusCounts(tenantId, ownerId));
        return response;
    }

    private long countTodayFollowup(
            Long tenantId,
            Long ownerId,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<FollowupRecordEntity> wrapper = followupBase(tenantId, ownerId);
        wrapper.ge("followup_at", todayStart);
        wrapper.lt("followup_at", tomorrowStart);
        return count(followupRecordMapper.selectCount(wrapper));
    }

    private long countTodayChannel(
            Long tenantId,
            Long ownerId,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<ChannelRecordEntity> wrapper = channelBase(tenantId, ownerId);
        wrapper.ge("created_at", todayStart);
        wrapper.lt("created_at", tomorrowStart);
        return count(channelRecordMapper.selectCount(wrapper));
    }

    private long countTodayLeadConversion(
            Long tenantId,
            Long ownerId,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<LeadEntity> wrapper = leadBase(tenantId, ownerId);
        wrapper.eq("status", LeadStatus.CONVERTED.name());
        wrapper.ge("converted_at", todayStart);
        wrapper.lt("converted_at", tomorrowStart);
        return count(leadMapper.selectCount(wrapper));
    }

    private long countTodayLead(
            Long tenantId,
            Long ownerId,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<LeadEntity> wrapper = leadBase(tenantId, ownerId);
        wrapper.ge("created_at", todayStart);
        wrapper.lt("created_at", tomorrowStart);
        return count(leadMapper.selectCount(wrapper));
    }

    private List<DashboardCountItem> buildLeadStatusCounts(Long tenantId, Long ownerId) {
        List<DashboardCountItem> items = new ArrayList<DashboardCountItem>();
        for (LeadStatus status : LeadStatus.values()) {
            QueryWrapper<LeadEntity> wrapper = leadBase(tenantId, ownerId);
            wrapper.eq("status", status.name());
            items.add(countItem(
                    status.name(),
                    leadStatusName(status),
                    count(leadMapper.selectCount(wrapper)),
                    BigDecimal.ZERO));
        }
        return items;
    }

    private List<DashboardCountItem> buildCustomerStatusCounts(Long tenantId, Long ownerId) {
        List<DashboardCountItem> items = new ArrayList<DashboardCountItem>();
        for (CustomerStatus status : CustomerStatus.values()) {
            QueryWrapper<CustomerEntity> wrapper = customerBase(tenantId, ownerId);
            wrapper.eq("status", status.name());
            items.add(countItem(
                    status.name(),
                    customerStatusName(status),
                    count(customerMapper.selectCount(wrapper)),
                    BigDecimal.ZERO));
        }
        return items;
    }

    private List<DashboardCountItem> buildOpportunityStageCounts(Long tenantId, Long ownerId) {
        List<DashboardCountItem> items = new ArrayList<DashboardCountItem>();
        for (OpportunityStage stage : OpportunityStage.values()) {
            QueryWrapper<OpportunityEntity> wrapper = opportunityBase(tenantId, ownerId);
            wrapper.eq("stage", stage.name());
            items.add(countItem(
                    stage.name(),
                    opportunityStageName(stage),
                    count(opportunityMapper.selectCount(wrapper)),
                    sumOpportunityAmount(tenantId, ownerId, stage)));
        }
        return items;
    }

    private List<DashboardCountItem> buildChannelStatusCounts(Long tenantId, Long ownerId) {
        List<DashboardCountItem> items = new ArrayList<DashboardCountItem>();
        for (ChannelStatus status : ChannelStatus.values()) {
            QueryWrapper<ChannelRecordEntity> wrapper = channelBase(tenantId, ownerId);
            wrapper.eq("status", status.name());
            items.add(countItem(
                    status.name(),
                    channelStatusName(status),
                    count(channelRecordMapper.selectCount(wrapper)),
                    BigDecimal.ZERO));
        }
        return items;
    }

    private BigDecimal sumOpportunityAmount(Long tenantId, Long ownerId, OpportunityStage stage) {
        QueryWrapper<OpportunityEntity> wrapper = opportunityBase(tenantId, ownerId);
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

    private QueryWrapper<LeadEntity> leadBase(Long tenantId, Long ownerId) {
        QueryWrapper<LeadEntity> wrapper = new QueryWrapper<LeadEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, ownerId);
        return wrapper;
    }

    private QueryWrapper<CustomerEntity> customerBase(Long tenantId, Long ownerId) {
        QueryWrapper<CustomerEntity> wrapper = new QueryWrapper<CustomerEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, ownerId);
        return wrapper;
    }

    private QueryWrapper<OpportunityEntity> opportunityBase(Long tenantId, Long ownerId) {
        QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, ownerId);
        return wrapper;
    }

    private QueryWrapper<ChannelRecordEntity> channelBase(Long tenantId, Long ownerId) {
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, ownerId);
        return wrapper;
    }

    private QueryWrapper<FollowupRecordEntity> followupBase(Long tenantId, Long ownerId) {
        QueryWrapper<FollowupRecordEntity> wrapper = new QueryWrapper<FollowupRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, ownerId);
        return wrapper;
    }

    private void applyOwnerScope(QueryWrapper<?> wrapper, Long ownerId) {
        if (ownerId != null) {
            wrapper.eq("owner_id", ownerId);
        }
    }

    private long count(Long value) {
        return value == null ? 0L : value.longValue();
    }

    private DashboardCountItem countItem(String code, String name, long count, BigDecimal amount) {
        DashboardCountItem item = new DashboardCountItem();
        item.setCode(code);
        item.setName(name);
        item.setCount(count);
        item.setAmount(amount == null ? BigDecimal.ZERO : amount);
        return item;
    }

    private String leadStatusName(LeadStatus status) {
        switch (status) {
            case NEW:
                return "新线索";
            case CONTACTED:
                return "已联系";
            case FOLLOWING:
                return "跟进中";
            case QUALIFIED:
                return "有效线索";
            case NURTURING:
                return "长期培育";
            case CONVERTED:
                return "已转化";
            case INVALID:
                return "无效线索";
            case DUPLICATE:
                return "重复线索";
            case CLOSED:
                return "已关闭";
            default:
                return status.name();
        }
    }

    private String customerStatusName(CustomerStatus status) {
        switch (status) {
            case POTENTIAL:
                return "潜在客户";
            case ACTIVE:
                return "正常经营";
            case DEALING:
                return "商机推进";
            case COOPERATED:
                return "已合作";
            case SLEEPING:
                return "沉睡客户";
            case CHURNED:
                return "已流失";
            case BLACKLIST:
                return "黑名单";
            default:
                return status.name();
        }
    }

    private String opportunityStageName(OpportunityStage stage) {
        switch (stage) {
            case DISCOVERY:
                return "需求发现";
            case QUALIFICATION:
                return "资格确认";
            case PROPOSAL:
                return "方案报价";
            case NEGOTIATION:
                return "商务谈判";
            case WON:
                return "已成交";
            case LOST:
                return "已丢单";
            default:
                return stage.name();
        }
    }

    private String channelStatusName(ChannelStatus status) {
        switch (status) {
            case NEW:
                return "新渠道";
            case WAITING_TRANSCRIPTION:
                return "待转译";
            case TRANSCRIBED:
                return "已转译";
            case WAITING_AI_ANALYSIS:
                return "待AI分析";
            case ANALYZED:
                return "已分析";
            case PROMOTED:
                return "已晋升";
            default:
                return status.name();
        }
    }
}
