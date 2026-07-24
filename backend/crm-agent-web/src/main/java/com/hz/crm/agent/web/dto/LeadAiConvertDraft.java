package com.hz.crm.agent.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadAiConvertDraft {

    private String customerName;

    private String industry;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private String level;

    private String status;

    private Long ownerId;

    private String remark;
}
