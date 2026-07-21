package com.hz.crm.application.lead.dto;

import com.hz.crm.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadQuery extends PageQuery {

    private String keyword;
}
