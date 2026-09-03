package com.hz.crm.application.dashboard.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardOverviewResponse {

    private long leadCount;

    private long customerCount;

    private long todayNewCustomerCount;

    private long monthNewCustomerCount;

    private long monthNewLeadCount;

    private long normalFollowupCustomerCount;

    private long warningFollowupCustomerCount;

    private long criticalFollowupCustomerCount;

    private long normalFollowupLeadCount;

    private long warningFollowupLeadCount;

    private long criticalFollowupLeadCount;

    private boolean managementView;

    private long opportunityCount;

    private long channelCount;

    private BigDecimal opportunityAmount = BigDecimal.ZERO;

    private BigDecimal wonAmount = BigDecimal.ZERO;

    private long todayFollowupCount;

    private long todayChannelUserCount;

    private long todayLeadConversionCount;

    private long todayNewLeadCount;

    private long todayPendingTaskCount;

    private long overdueTaskCount;

    private long todayCompletedTaskCount;

    private List<DashboardFollowupRankItem> todayFollowupRanking = new ArrayList<DashboardFollowupRankItem>();

    private List<DashboardTaskRankItem> todayTaskCompletionRanking = new ArrayList<DashboardTaskRankItem>();

    private List<DashboardFollowupAttentionItem> attentionCustomers =
            new ArrayList<DashboardFollowupAttentionItem>();

    private List<DashboardFollowupAttentionItem> attentionLeads =
            new ArrayList<DashboardFollowupAttentionItem>();
}
