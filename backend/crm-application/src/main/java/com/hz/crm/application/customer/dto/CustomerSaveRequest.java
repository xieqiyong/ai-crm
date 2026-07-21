package com.hz.crm.application.customer.dto;

import com.hz.crm.domain.customer.CustomerLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerSaveRequest {

    private Long id;

    @NotBlank(message = "客户名称不能为空")
    private String name;

    private String industry;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private CustomerLevel level;

    private Long ownerId;

    private String remark;
}
