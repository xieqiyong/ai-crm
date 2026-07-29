package com.hz.crm.application.lead.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadImportRow {

    private int rowNumber;

    private String name;

    private String companyName;

    private String phone;

    private String email;

    private String source;

    private Map<String, String> additionalFields = new LinkedHashMap<String, String>();
}
