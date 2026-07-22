package com.hz.crm.application.dashboard.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardCountItem {

    private String code;

    private String name;

    private long count;

    private BigDecimal amount = BigDecimal.ZERO;
}
