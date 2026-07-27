package com.hz.crm.application.channel.dto;

import com.hz.crm.common.api.PageQuery;
import com.hz.crm.domain.channel.MarketingFormStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingFormQuery extends PageQuery {

    private String keyword;

    private MarketingFormStatus status;
}
