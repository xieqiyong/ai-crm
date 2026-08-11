package com.hz.crm.agent.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LeadAiCustomerProfile {

    private Boolean available;

    private String companyName;

    private String creditCode;

    private String legalRepresentative;

    private String keyPerson;

    private String companyScale;

    private String industry;

    private String phone;

    private String email;

    private String website;

    private String address;

    private String registeredCapital;

    private String establishDate;

    private String description;

    private String sourceSummary;

    private String searchedAt;

    private List<String> sourceUrls = new ArrayList<String>();
}
