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

    private long opportunityCount;

    private long channelCount;

    private BigDecimal opportunityAmount = BigDecimal.ZERO;

    private BigDecimal wonAmount = BigDecimal.ZERO;

    private long todayFollowupCount;

    private long todayChannelUserCount;

    private long todayLeadConversionCount;

    private long todayNewLeadCount;

    private List<DashboardCountItem> leadStatusCounts = new ArrayList<DashboardCountItem>();

    private List<DashboardCountItem> customerStatusCounts = new ArrayList<DashboardCountItem>();

    private List<DashboardCountItem> opportunityStageCounts = new ArrayList<DashboardCountItem>();

    private List<DashboardCountItem> channelStatusCounts = new ArrayList<DashboardCountItem>();
}
