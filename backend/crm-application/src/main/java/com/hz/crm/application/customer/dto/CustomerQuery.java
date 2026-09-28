package com.hz.crm.application.customer.dto;

import com.hz.crm.common.api.PageQuery;
import com.hz.crm.application.marketing.MarketingFunnelStage;
import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.CustomerStatus;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerQuery extends PageQuery {

    private String keyword;

    private CustomerStatus status;

    private List<CustomerLevel> levels;

    private MarketingFunnelStage funnelStage;
}
