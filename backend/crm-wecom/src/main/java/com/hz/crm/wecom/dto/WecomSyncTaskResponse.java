package com.hz.crm.wecom.dto;

import com.hz.crm.domain.wecom.WecomSyncStatus;
import com.hz.crm.domain.wecom.WecomSyncTrigger;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WecomSyncTaskResponse {

    private Long id;

    private Long configId;

    private WecomSyncTrigger triggerType;

    private WecomSyncStatus status;

    private Integer contactsFetched;

    private Integer contactsCreated;

    private Integer contactsUpdated;

    private Integer groupsFetched;

    private Integer groupMembersFetched;

    private Integer channelsCreated;

    private Integer channelsUpdated;

    private Integer duplicatesSkipped;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String errorMessage;
}
