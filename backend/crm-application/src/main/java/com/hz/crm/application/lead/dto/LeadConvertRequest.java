package com.hz.crm.application.lead.dto;

import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.CustomerStatus;
import com.hz.crm.domain.lead.LeadConvertType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadConvertRequest {

    @NotNull(message = "线索编号不能为空")
    private Long leadId;

    private LeadConvertType convertType;

    private Long customerId;

    private String customerName;

    private String industry;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private CustomerLevel level;

    private CustomerStatus status;

    private Long ownerId;

    private String remark;
}
