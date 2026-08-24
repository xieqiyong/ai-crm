package com.hz.crm.application.channel.dto;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSourceImportRow {

    private int rowNumber;

    private String contactName;

    private String companyName;

    private String phone;

    private String email;

    private LocalDateTime submittedAt;

    private Map<String, String> values = new LinkedHashMap<String, String>();
}
