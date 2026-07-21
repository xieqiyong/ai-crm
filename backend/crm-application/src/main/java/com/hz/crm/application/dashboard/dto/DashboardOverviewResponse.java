package com.hz.crm.application.dashboard.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardOverviewResponse {

    private long leadCount;

    private long customerCount;

    private long opportunityCount;

    private BigDecimal opportunityAmount = BigDecimal.ZERO;
}
