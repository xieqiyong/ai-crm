package com.hz.crm.application.lead.dto;

import com.hz.crm.domain.lead.LeadStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadSaveRequest {

    private Long id;

    @NotBlank(message = "线索名称不能为空")
    private String name;

    private String companyName;

    private String phone;

    private String email;

    private String source;

    private LeadStatus status;

    private Long ownerId;

    private String remark;
}
