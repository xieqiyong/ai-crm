package com.hz.crm.agent.web.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadAiCustomerProfile {

    private Boolean available;

    private String companyName;

    private String legalRepresentative;

    private String keyPerson;

    private String companyScale;

    private String industry;

    private String phone;

    private String email;

    private String website;

    private String address;

    private String registeredCapital;

    private String sourceSummary;

    private String searchedAt;

    private List<String> sourceUrls = new ArrayList<String>();
}
