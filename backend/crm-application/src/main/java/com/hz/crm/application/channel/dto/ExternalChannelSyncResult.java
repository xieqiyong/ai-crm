package com.hz.crm.application.channel.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExternalChannelSyncResult {

    private Long channelId;

    private boolean created;

    private boolean updated;

    private boolean skipped;
}
