package com.hz.crm.application.channel.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicMarketingFormResponse {

    private String formCode;

    private String title;

    private String description;

    private String submitMessage;

    private List<MarketingFormFieldResponse> fields = new ArrayList<MarketingFormFieldResponse>();
}
