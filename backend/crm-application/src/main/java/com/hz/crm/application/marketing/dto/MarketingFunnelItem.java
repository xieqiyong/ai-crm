package com.hz.crm.application.marketing.dto;

import com.hz.crm.application.marketing.MarketingFunnelStage;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingFunnelItem {

    private MarketingFunnelStage code;

    private String name;

    private String description;

    private Long count;

    private String targetRoute;

    private boolean accessible;
}
