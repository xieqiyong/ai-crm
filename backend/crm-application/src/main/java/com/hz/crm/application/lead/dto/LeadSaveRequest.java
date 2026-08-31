package com.hz.crm.application.lead.dto;

import com.hz.crm.domain.lead.LeadStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadSaveRequest {

    private Long id;

    private String name;

    private String companyName;

    private String phone;

    private String email;

    private String source;

    private LeadStatus status;

    private Long ownerId;

    @NotNull(message = "意向产品不能为空")
    private Long productId;

    private String remark;
}
