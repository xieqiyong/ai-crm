package com.hz.crm.application.channel.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicMarketingFormSubmitRequest {

    private String formCode;

    private Map<String, String> values = new HashMap<String, String>();
}
