package com.hz.crm.agent.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadAiAnalyzeRequest {

    @NotNull(message = "线索编号不能为空")
    private Long leadId;

    private String instruction;
}
