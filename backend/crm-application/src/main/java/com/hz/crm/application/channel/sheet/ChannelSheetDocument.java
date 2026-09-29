package com.hz.crm.application.channel.sheet;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSheetDocument {

    private String platform;

    private Long configId;

    private String docId;

    private String sheetId;

    private String sourceUrl;
}
