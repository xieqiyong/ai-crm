package com.hz.crm.application.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.dashboard.dto.DashboardFollowupAttentionItem;
import com.hz.crm.application.dashboard.dto.DashboardFollowupRankItem;
import com.hz.crm.application.dashboard.dto.DashboardOverviewResponse;
import com.hz.crm.application.dashboard.dto.DashboardTaskRankItem;
import com.hz.crm.application.system.SystemParameterApplicationService;
import com.hz.crm.application.system.dto.FollowupTaskSettingsResponse;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerFollowupProjection;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.followup.FollowupRankingProjection;
import com.hz.crm.domain.followup.FollowupRecordEntity;
import com.hz.crm.domain.followup.mapper.FollowupRecordMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadFollowupProjection;
import com.hz.crm.domain.lead.LeadStatus;
import com.hz.crm.domain.lead.mapper.LeadMapper;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityStage;
import com.hz.crm.domain.opportunity.mapper.OpportunityMapper;
import com.hz.crm.domain.task.SalesTaskEntity;
import com.hz.crm.domain.task.SalesTaskStatus;
import com.hz.crm.domain.task.TaskCompletionRankingProjection;
import com.hz.crm.domain.task.mapper.SalesTaskMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    @Autowired
    private SalesTaskMapper salesTaskMapper;

    @Autowired
    private SystemParameterApplicationService systemParameterApplicationService;

    @Autowired
    private UserDataScopeValidator userDataScopeValidator;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(Long tenantId, Long userId, String dataScope) {
        boolean scopeRestricted = !"ALL".equals(dataScope);
        List<Long> ownerIds = scopeRestricted
                ? userDataScopeValidator.listAccessibleUserIds(tenantId, userId, dataScope)
                : new ArrayList<Long>();
        LocalDateTime now = DateTimes.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        DashboardOverviewResponse response = new DashboardOverviewResponse();
        response.setManagementView(!"SELF".equals(dataScope));
        response.setLeadCount(count(leadMapper.selectCount(leadBase(tenantId, scopeRestricted, ownerIds))));
        response.setCustomerCount(count(customerMapper.selectCount(customerBase(tenantId, scopeRestricted, ownerIds))));
        response.setOpportunityCount(count(
                opportunityMapper.selectCount(opportunityBase(tenantId, scopeRestricted, ownerIds))));
        response.setChannelCount(count(
                channelRecordMapper.selectCount(channelBase(tenantId, scopeRestricted, ownerIds))));
        response.setOpportunityAmount(sumOpportunityAmount(tenantId, scopeRestricted, ownerIds, null));
        response.setWonAmount(sumOpportunityAmount(tenantId, scopeRestricted, ownerIds, OpportunityStage.WON));
        response.setTodayFollowupCount(
                countTodayFollowup(tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
        response.setTodayChannelUserCount(
                countTodayChannel(tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
        response.setTodayLeadConversionCount(
                countTodayLeadConversion(tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
        response.setTodayNewLeadCount(
                countTodayLead(tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
        response.setTodayPendingTaskCount(
                countTodayPendingTask(tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
        response.setOverdueTaskCount(countOverdueTask(tenantId, scopeRestricted, ownerIds, now));
        response.setTodayCompletedTaskCount(
                countTodayCompletedTask(tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
        response.setTodayNewCustomerCount(
                countCustomersCreatedBetween(tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
        response.setMonthNewCustomerCount(
                countCustomersCreatedBetween(tenantId, scopeRestricted, ownerIds, monthStart, tomorrowStart));
        response.setMonthNewLeadCount(
                countLeadsCreatedBetween(tenantId, scopeRestricted, ownerIds, monthStart, tomorrowStart));
        if (response.isManagementView()) {
            response.setTodayFollowupRanking(buildTodayFollowupRanking(
                    tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
            response.setTodayTaskCompletionRanking(buildTodayTaskCompletionRanking(
                    tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart));
        }
        FollowupTaskSettingsResponse followupSettings =
                systemParameterApplicationService.followupTaskSettings(tenantId);
        fillCustomerFollowupHealth(
                response, tenantId, scopeRestricted, ownerIds, now, followupSettings);
        fillLeadFollowupHealth(
                response, tenantId, scopeRestricted, ownerIds, now, followupSettings);
        return response;
    }

    private List<DashboardTaskRankItem> buildTodayTaskCompletionRanking(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        List<TaskCompletionRankingProjection> rows =
                salesTaskMapper.selectTodayCompletionRanking(
                        tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart, 10);
        List<DashboardTaskRankItem> items = new ArrayList<DashboardTaskRankItem>();
        int rankNo = 1;
        for (TaskCompletionRankingProjection row : rows) {
            DashboardTaskRankItem item = new DashboardTaskRankItem();
            item.setRankNo(rankNo);
            item.setUserId(row.getUserId());
            item.setUserName(row.getUserName());
            item.setCompletedTaskCount(
                    row.getCompletedTaskCount() == null ? 0L : row.getCompletedTaskCount().longValue());
            item.setLastCompletedAt(row.getLastCompletedAt());
            items.add(item);
            rankNo++;
        }
        return items;
    }

    private List<DashboardFollowupRankItem> buildTodayFollowupRanking(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        List<FollowupRankingProjection> rows =
                followupRecordMapper.selectTodayFollowupRanking(
                        tenantId, scopeRestricted, ownerIds, todayStart, tomorrowStart, 10);
        List<DashboardFollowupRankItem> items = new ArrayList<DashboardFollowupRankItem>();
        int rankNo = 1;
        for (FollowupRankingProjection row : rows) {
            DashboardFollowupRankItem item = new DashboardFollowupRankItem();
            item.setRankNo(rankNo);
            item.setUserId(row.getUserId());
            item.setUserName(row.getUserName());
            item.setFollowupCount(row.getFollowupCount() == null ? 0L : row.getFollowupCount().longValue());
            item.setLastFollowupAt(row.getLastFollowupAt());
            items.add(item);
            rankNo++;
        }
        return items;
    }

    private long countTodayPendingTask(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<SalesTaskEntity> wrapper = salesTaskBase(tenantId, scopeRestricted, ownerIds);
        wrapper.in("status", SalesTaskStatus.PENDING.name(), SalesTaskStatus.IN_PROGRESS.name());
        wrapper.ge("due_at", todayStart);
        wrapper.lt("due_at", tomorrowStart);
        return count(salesTaskMapper.selectCount(wrapper));
    }

    private long countOverdueTask(
            Long tenantId, boolean scopeRestricted, List<Long> ownerIds, LocalDateTime now) {
        QueryWrapper<SalesTaskEntity> wrapper = salesTaskBase(tenantId, scopeRestricted, ownerIds);
        wrapper.in(
                "status",
                SalesTaskStatus.PENDING.name(),
                SalesTaskStatus.IN_PROGRESS.name(),
                SalesTaskStatus.OVERDUE.name());
        wrapper.lt("due_at", now);
        return count(salesTaskMapper.selectCount(wrapper));
    }

    private long countTodayCompletedTask(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<SalesTaskEntity> wrapper = salesTaskBase(tenantId, scopeRestricted, ownerIds);
        wrapper.eq("status", SalesTaskStatus.COMPLETED.name());
        wrapper.ge("completed_at", todayStart);
        wrapper.lt("completed_at", tomorrowStart);
        return count(salesTaskMapper.selectCount(wrapper));
    }

    private long countTodayFollowup(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<FollowupRecordEntity> wrapper = followupBase(tenantId, scopeRestricted, ownerIds);
        wrapper.ge("followup_at", todayStart);
        wrapper.lt("followup_at", tomorrowStart);
        return count(followupRecordMapper.selectCount(wrapper));
    }

    private long countTodayChannel(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<ChannelRecordEntity> wrapper = channelBase(tenantId, scopeRestricted, ownerIds);
        wrapper.ge("created_at", todayStart);
        wrapper.lt("created_at", tomorrowStart);
        return count(channelRecordMapper.selectCount(wrapper));
    }

    private long countTodayLeadConversion(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<LeadEntity> wrapper = leadBase(tenantId, scopeRestricted, ownerIds);
        wrapper.eq("status", LeadStatus.CONVERTED.name());
        wrapper.ge("converted_at", todayStart);
        wrapper.lt("converted_at", tomorrowStart);
        return count(leadMapper.selectCount(wrapper));
    }

    private long countTodayLead(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        QueryWrapper<LeadEntity> wrapper = leadBase(tenantId, scopeRestricted, ownerIds);
        wrapper.ge("created_at", todayStart);
        wrapper.lt("created_at", tomorrowStart);
        return count(leadMapper.selectCount(wrapper));
    }

    private BigDecimal sumOpportunityAmount(
            Long tenantId, boolean scopeRestricted, List<Long> ownerIds, OpportunityStage stage) {
        QueryWrapper<OpportunityEntity> wrapper = opportunityBase(tenantId, scopeRestricted, ownerIds);
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

    private long countCustomersCreatedBetween(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime start,
            LocalDateTime end) {
        QueryWrapper<CustomerEntity> wrapper = customerBase(tenantId, scopeRestricted, ownerIds);
        wrapper.ge("created_at", start);
        wrapper.lt("created_at", end);
        return count(customerMapper.selectCount(wrapper));
    }

    private long countLeadsCreatedBetween(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime start,
            LocalDateTime end) {
        QueryWrapper<LeadEntity> wrapper = leadBase(tenantId, scopeRestricted, ownerIds);
        wrapper.ge("created_at", start);
        wrapper.lt("created_at", end);
        return count(leadMapper.selectCount(wrapper));
    }

    private void fillCustomerFollowupHealth(
            DashboardOverviewResponse response,
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime now,
            FollowupTaskSettingsResponse followupSettings) {
        int firstDelayMinutes = followupSettings.getFirstDelayMinutes();
        int configuredSecondDelayMinutes = followupSettings.getSecondDelayMinutes();
        int secondDelayMinutes = Math.max(firstDelayMinutes + 1, configuredSecondDelayMinutes);
        int overdueGraceMinutes = secondDelayMinutes - firstDelayMinutes;
        List<CustomerFollowupProjection> records =
                customerMapper.selectFollowupCustomers(tenantId, scopeRestricted, ownerIds);
        List<DashboardFollowupAttentionItem> attentionItems =
                new ArrayList<DashboardFollowupAttentionItem>();
        long normalCount = 0L;
        long warningCount = 0L;
        long criticalCount = 0L;
        for (CustomerFollowupProjection record : records) {
            DashboardFollowupAttentionItem item = buildCustomerAttentionItem(record);
            applyFollowupHealth(
                    item,
                    now,
                    record.getCreatedAt(),
                    record.getLastFollowupAt(),
                    record.getNextFollowTime(),
                    firstDelayMinutes,
                    secondDelayMinutes,
                    overdueGraceMinutes,
                    "客户");
            if ("CRITICAL".equals(item.getFollowupHealth())) {
                criticalCount++;
                attentionItems.add(item);
            } else if ("WARNING".equals(item.getFollowupHealth())) {
                warningCount++;
                attentionItems.add(item);
            } else {
                normalCount++;
            }
        }
        attentionItems = sortAndLimitAttentionItems(attentionItems);
        response.setNormalFollowupCustomerCount(normalCount);
        response.setWarningFollowupCustomerCount(warningCount);
        response.setCriticalFollowupCustomerCount(criticalCount);
        response.setAttentionCustomers(attentionItems);
    }

    private DashboardFollowupAttentionItem buildCustomerAttentionItem(
            CustomerFollowupProjection record) {
        DashboardFollowupAttentionItem item = new DashboardFollowupAttentionItem();
        item.setTargetType("CUSTOMER");
        item.setTargetId(record.getCustomerId());
        item.setTargetName(record.getCustomerName());
        item.setContactName(record.getContactName());
        item.setContactPhone(record.getContactPhone());
        item.setProductName(record.getProductName());
        item.setOwnerId(record.getOwnerId());
        item.setOwnerName(record.getOwnerName());
        item.setLastFollowupAt(record.getLastFollowupAt());
        item.setNextFollowTime(record.getNextFollowTime());
        return item;
    }

    private void fillLeadFollowupHealth(
            DashboardOverviewResponse response,
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            LocalDateTime now,
            FollowupTaskSettingsResponse followupSettings) {
        int firstDelayMinutes = followupSettings.getFirstDelayMinutes();
        int configuredSecondDelayMinutes = followupSettings.getSecondDelayMinutes();
        int secondDelayMinutes = Math.max(firstDelayMinutes + 1, configuredSecondDelayMinutes);
        int overdueGraceMinutes = secondDelayMinutes - firstDelayMinutes;
        List<LeadFollowupProjection> records =
                leadMapper.selectFollowupLeads(tenantId, scopeRestricted, ownerIds);
        List<DashboardFollowupAttentionItem> attentionItems =
                new ArrayList<DashboardFollowupAttentionItem>();
        long normalCount = 0L;
        long warningCount = 0L;
        long criticalCount = 0L;
        for (LeadFollowupProjection record : records) {
            DashboardFollowupAttentionItem item = buildLeadAttentionItem(record);
            applyFollowupHealth(
                    item,
                    now,
                    record.getCreatedAt(),
                    record.getLastFollowupAt(),
                    record.getNextFollowTime(),
                    firstDelayMinutes,
                    secondDelayMinutes,
                    overdueGraceMinutes,
                    "线索");
            if ("CRITICAL".equals(item.getFollowupHealth())) {
                criticalCount++;
                attentionItems.add(item);
            } else if ("WARNING".equals(item.getFollowupHealth())) {
                warningCount++;
                attentionItems.add(item);
            } else {
                normalCount++;
            }
        }
        response.setNormalFollowupLeadCount(normalCount);
        response.setWarningFollowupLeadCount(warningCount);
        response.setCriticalFollowupLeadCount(criticalCount);
        response.setAttentionLeads(sortAndLimitAttentionItems(attentionItems));
    }

    private DashboardFollowupAttentionItem buildLeadAttentionItem(LeadFollowupProjection record) {
        DashboardFollowupAttentionItem item = new DashboardFollowupAttentionItem();
        item.setTargetType("LEAD");
        item.setTargetId(record.getLeadId());
        item.setTargetName(record.getLeadName());
        item.setCompanyName(record.getCompanyName());
        item.setContactName(record.getLeadName());
        item.setContactPhone(record.getPhone());
        item.setProductName(record.getProductName());
        item.setOwnerId(record.getOwnerId());
        item.setOwnerName(record.getOwnerName());
        item.setLastFollowupAt(record.getLastFollowupAt());
        item.setNextFollowTime(record.getNextFollowTime());
        return item;
    }

    private void applyFollowupHealth(
            DashboardFollowupAttentionItem item,
            LocalDateTime now,
            LocalDateTime createdAt,
            LocalDateTime lastFollowupAt,
            LocalDateTime nextFollowTime,
            int firstDelayMinutes,
            int secondDelayMinutes,
            int overdueGraceMinutes,
            String targetTypeName) {
        LocalDateTime warningAt;
        LocalDateTime criticalAt;
        if (nextFollowTime != null) {
            warningAt = nextFollowTime;
            criticalAt = warningAt.plusMinutes(overdueGraceMinutes);
        } else {
            LocalDateTime baseTime = lastFollowupAt == null ? createdAt : lastFollowupAt;
            warningAt = baseTime.plusMinutes(firstDelayMinutes);
            criticalAt = baseTime.plusMinutes(secondDelayMinutes);
        }
        item.setWarningAt(warningAt);
        if (!now.isBefore(criticalAt)) {
            item.setFollowupHealth("CRITICAL");
            item.setFollowupHealthName("严重不足");
            item.setFollowupReason(followupReason(nextFollowTime, lastFollowupAt, targetTypeName, true));
        } else if (!now.isBefore(warningAt)) {
            item.setFollowupHealth("WARNING");
            item.setFollowupHealthName("需要跟进");
            item.setFollowupReason(followupReason(nextFollowTime, lastFollowupAt, targetTypeName, false));
        } else {
            item.setFollowupHealth("NORMAL");
            item.setFollowupHealthName("跟进正常");
            item.setFollowupReason("当前跟进节奏正常");
        }
    }

    private String followupReason(
            LocalDateTime nextFollowTime,
            LocalDateTime lastFollowupAt,
            String targetTypeName,
            boolean critical) {
        if (nextFollowTime != null) {
            return critical ? "下次跟进时间已严重超期" : "已到计划跟进时间";
        }
        if (lastFollowupAt == null) {
            return critical
                    ? targetTypeName + "创建后长时间未跟进"
                    : targetTypeName + "创建后尚未跟进";
        }
        return critical ? "距离上次跟进时间过长" : "已进入跟进提醒周期";
    }

    private List<DashboardFollowupAttentionItem> sortAndLimitAttentionItems(
            List<DashboardFollowupAttentionItem> attentionItems) {
        Collections.sort(attentionItems, new Comparator<DashboardFollowupAttentionItem>() {
            @Override
            public int compare(DashboardFollowupAttentionItem left, DashboardFollowupAttentionItem right) {
                int healthCompare = Integer.compare(healthWeight(left), healthWeight(right));
                if (healthCompare != 0) {
                    return healthCompare;
                }
                return compareDateTime(left.getWarningAt(), right.getWarningAt());
            }
        });
        if (attentionItems.size() > 10) {
            return new ArrayList<DashboardFollowupAttentionItem>(attentionItems.subList(0, 10));
        }
        return attentionItems;
    }

    private int healthWeight(DashboardFollowupAttentionItem item) {
        return "CRITICAL".equals(item.getFollowupHealth()) ? 0 : 1;
    }

    private int compareDateTime(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private QueryWrapper<LeadEntity> leadBase(
            Long tenantId, boolean scopeRestricted, List<Long> ownerIds) {
        QueryWrapper<LeadEntity> wrapper = new QueryWrapper<LeadEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, scopeRestricted, ownerIds);
        return wrapper;
    }

    private QueryWrapper<CustomerEntity> customerBase(
            Long tenantId, boolean scopeRestricted, List<Long> ownerIds) {
        QueryWrapper<CustomerEntity> wrapper = new QueryWrapper<CustomerEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, scopeRestricted, ownerIds);
        return wrapper;
    }

    private QueryWrapper<OpportunityEntity> opportunityBase(
            Long tenantId, boolean scopeRestricted, List<Long> ownerIds) {
        QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, scopeRestricted, ownerIds);
        return wrapper;
    }

    private QueryWrapper<ChannelRecordEntity> channelBase(
            Long tenantId, boolean scopeRestricted, List<Long> ownerIds) {
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, scopeRestricted, ownerIds);
        return wrapper;
    }

    private QueryWrapper<FollowupRecordEntity> followupBase(
            Long tenantId, boolean scopeRestricted, List<Long> ownerIds) {
        QueryWrapper<FollowupRecordEntity> wrapper = new QueryWrapper<FollowupRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, scopeRestricted, ownerIds);
        return wrapper;
    }

    private QueryWrapper<SalesTaskEntity> salesTaskBase(
            Long tenantId, boolean scopeRestricted, List<Long> ownerIds) {
        QueryWrapper<SalesTaskEntity> wrapper = new QueryWrapper<SalesTaskEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        applyOwnerScope(wrapper, scopeRestricted, ownerIds);
        return wrapper;
    }

    private void applyOwnerScope(
            QueryWrapper<?> wrapper, boolean scopeRestricted, List<Long> ownerIds) {
        if (!scopeRestricted) {
            return;
        }
        if (ownerIds == null || ownerIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in("owner_id", ownerIds);
        }
    }

    private long count(Long value) {
        return value == null ? 0L : value.longValue();
    }

}
