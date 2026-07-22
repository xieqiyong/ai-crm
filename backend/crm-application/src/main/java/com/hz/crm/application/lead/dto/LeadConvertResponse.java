package com.hz.crm.application.lead.dto;

import com.hz.crm.application.customer.dto.CustomerResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadConvertResponse {

    private LeadResponse lead;

    private CustomerResponse customer;
}
