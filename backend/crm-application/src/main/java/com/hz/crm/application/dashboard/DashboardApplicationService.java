package com.hz.crm.application.dashboard;

import com.hz.crm.application.dashboard.dto.DashboardCountItem;
import com.hz.crm.application.dashboard.dto.DashboardOverviewResponse;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.repository.ChannelRecordRepository;
import com.hz.crm.domain.customer.CustomerStatus;
import com.hz.crm.domain.customer.repository.CustomerJpaRepository;
import com.hz.crm.domain.lead.LeadStatus;
import com.hz.crm.domain.lead.repository.LeadJpaRepository;
import com.hz.crm.domain.opportunity.OpportunityStage;
import com.hz.crm.domain.opportunity.repository.OpportunityJpaRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardApplicationService {

    @Autowired
    private LeadJpaRepository leadRepository;

    @Autowired
    private CustomerJpaRepository customerRepository;

    @Autowired
    private OpportunityJpaRepository opportunityRepository;

    @Autowired
    private ChannelRecordRepository channelRecordRepository;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(String tenantId, Long userId, String dataScope) {
        DashboardOverviewResponse response = new DashboardOverviewResponse();
        Long ownerId = "SELF".equals(dataScope) ? userId : null;
        response.setLeadCount(countLead(tenantId, ownerId));
        response.setCustomerCount(countCustomer(tenantId, ownerId));
        response.setOpportunityCount(countOpportunity(tenantId, ownerId));
        response.setChannelCount(countChannel(tenantId, ownerId));
        response.setOpportunityAmount(nullToZero(opportunityRepository.sumAmount(tenantId, ownerId)));
        response.setWonAmount(nullToZero(opportunityRepository.sumAmountByStage(tenantId, ownerId, OpportunityStage.WON)));
        response.setLeadStatusCounts(buildLeadStatusCounts(tenantId, ownerId));
        response.setCustomerStatusCounts(buildCustomerStatusCounts(tenantId, ownerId));
        response.setOpportunityStageCounts(buildOpportunityStageCounts(tenantId, ownerId));
        response.setChannelStatusCounts(buildChannelStatusCounts(tenantId, ownerId));
        return response;
    }

    private long countLead(String tenantId, Long ownerId) {
        if (ownerId == null) {
            return leadRepository.countByTenantIdAndDeletedFalse(tenantId);
        }
        return leadRepository.countByTenantIdAndOwnerIdAndDeletedFalse(tenantId, ownerId);
    }

    private long countCustomer(String tenantId, Long ownerId) {
        if (ownerId == null) {
            return customerRepository.countByTenantIdAndDeletedFalse(tenantId);
        }
        return customerRepository.countByTenantIdAndOwnerIdAndDeletedFalse(tenantId, ownerId);
    }

    private long countOpportunity(String tenantId, Long ownerId) {
        if (ownerId == null) {
            return opportunityRepository.countByTenantIdAndDeletedFalse(tenantId);
        }
        return opportunityRepository.countByTenantIdAndOwnerIdAndDeletedFalse(tenantId, ownerId);
    }

    private long countChannel(String tenantId, Long ownerId) {
        if (ownerId == null) {
            return channelRecordRepository.countByTenantIdAndDeletedFalse(tenantId);
        }
        return channelRecordRepository.countByTenantIdAndOwnerIdAndDeletedFalse(tenantId, ownerId);
    }

    private List<DashboardCountItem> buildLeadStatusCounts(String tenantId, Long ownerId) {
        List<DashboardCountItem> items = new ArrayList<DashboardCountItem>();
        for (LeadStatus status : LeadStatus.values()) {
            long count = ownerId == null
                    ? leadRepository.countByTenantIdAndStatusAndDeletedFalse(tenantId, status)
                    : leadRepository.countByTenantIdAndOwnerIdAndStatusAndDeletedFalse(tenantId, ownerId, status);
            items.add(countItem(status.name(), leadStatusName(status), count, BigDecimal.ZERO));
        }
        return items;
    }

    private List<DashboardCountItem> buildCustomerStatusCounts(String tenantId, Long ownerId) {
        List<DashboardCountItem> items = new ArrayList<DashboardCountItem>();
        for (CustomerStatus status : CustomerStatus.values()) {
            long count = ownerId == null
                    ? customerRepository.countByTenantIdAndStatusAndDeletedFalse(tenantId, status)
                    : customerRepository.countByTenantIdAndOwnerIdAndStatusAndDeletedFalse(tenantId, ownerId, status);
            items.add(countItem(status.name(), customerStatusName(status), count, BigDecimal.ZERO));
        }
        return items;
    }

    private List<DashboardCountItem> buildOpportunityStageCounts(String tenantId, Long ownerId) {
        List<DashboardCountItem> items = new ArrayList<DashboardCountItem>();
        for (OpportunityStage stage : OpportunityStage.values()) {
            long count = ownerId == null
                    ? opportunityRepository.countByTenantIdAndStageAndDeletedFalse(tenantId, stage)
                    : opportunityRepository.countByTenantIdAndOwnerIdAndStageAndDeletedFalse(tenantId, ownerId, stage);
            BigDecimal amount = opportunityRepository.sumAmountByStage(tenantId, ownerId, stage);
            items.add(countItem(stage.name(), opportunityStageName(stage), count, nullToZero(amount)));
        }
        return items;
    }

    private List<DashboardCountItem> buildChannelStatusCounts(String tenantId, Long ownerId) {
        List<DashboardCountItem> items = new ArrayList<DashboardCountItem>();
        for (ChannelStatus status : ChannelStatus.values()) {
            long count = ownerId == null
                    ? channelRecordRepository.countByTenantIdAndStatusAndDeletedFalse(tenantId, status)
                    : channelRecordRepository.countByTenantIdAndOwnerIdAndStatusAndDeletedFalse(tenantId, ownerId, status);
            items.add(countItem(status.name(), channelStatusName(status), count, BigDecimal.ZERO));
        }
        return items;
    }

    private DashboardCountItem countItem(String code, String name, long count, BigDecimal amount) {
        DashboardCountItem item = new DashboardCountItem();
        item.setCode(code);
        item.setName(name);
        item.setCount(count);
        item.setAmount(nullToZero(amount));
        return item;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
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
