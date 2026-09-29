package com.hz.crm.application.channel.sheet;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSheetRecord {

    private String recordId;

    private String version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Map<String, String> values = new LinkedHashMap<String, String>();
}
