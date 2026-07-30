package com.hz.crm.application.customer.dto;

import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerSaveRequest {

    private Long id;

    @NotBlank(message = "客户名称不能为空")
    private String name;

    @NotBlank(message = "行业不能为空")
    private String industry;

    @NotBlank(message = "主要联系人不能为空")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    @NotBlank(message = "联系邮箱不能为空")
    @Email(message = "联系邮箱格式不正确")
    private String contactEmail;

    @NotNull(message = "客户级别不能为空")
    private CustomerLevel level;

    @NotNull(message = "客户状态不能为空")
    private CustomerStatus status;

    @NotNull(message = "负责人不能为空")
    private Long ownerId;

    private String remark;
}
