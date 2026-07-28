package com.hz.crm.application.opportunity.dto;

import com.hz.crm.common.api.PageQuery;
import com.hz.crm.domain.opportunity.OpportunityStage;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpportunityQuery extends PageQuery {

    private String keyword;

    private OpportunityStage stage;

    private Long customerId;
}
