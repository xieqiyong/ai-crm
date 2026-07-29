package com.hz.crm.application.lead.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadImportError {

    private int rowNumber;

    private String name;

    private String companyName;

    private String type;

    private String reason;
}
