package com.hz.crm.application.customer.dto;

import com.hz.crm.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerQuery extends PageQuery {

    private String keyword;
}
