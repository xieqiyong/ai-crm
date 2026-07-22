package com.hz.crm.application.customer.dto;

import com.hz.crm.common.api.PageQuery;
import com.hz.crm.domain.customer.CustomerStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerQuery extends PageQuery {

    private String keyword;

    private CustomerStatus status;
}
