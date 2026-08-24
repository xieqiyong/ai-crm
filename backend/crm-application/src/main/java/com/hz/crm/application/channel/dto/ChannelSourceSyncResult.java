package com.hz.crm.application.channel.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSourceSyncResult {

    private Long sourceId;

    private Long logId;

    private int fetchedCount;

    private int createdCount;

    private int updatedCount;

    private int skippedCount;

    private int failedCount;

    private String message;
}
