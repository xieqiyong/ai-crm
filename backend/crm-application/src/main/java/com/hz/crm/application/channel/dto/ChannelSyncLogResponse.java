package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.ChannelSyncStatus;
import com.hz.crm.domain.channel.ChannelSyncTrigger;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSyncLogResponse {

    private Long id;

    private Long sourceId;

    private ChannelSyncTrigger triggerType;

    private ChannelSyncStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Integer fetchedCount;

    private Integer createdCount;

    private Integer updatedCount;

    private Integer skippedCount;

    private Integer failedCount;

    private String errorMessage;
}
