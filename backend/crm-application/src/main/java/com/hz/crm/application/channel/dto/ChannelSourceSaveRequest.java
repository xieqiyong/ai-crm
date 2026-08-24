package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.ChannelSourceKind;
import com.hz.crm.domain.channel.ChannelSourceStatus;
import com.hz.crm.domain.channel.ChannelSyncMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSourceSaveRequest {

    private Long id;

    private String name;

    private ChannelSourceKind sourceType;

    private ChannelSourceStatus status;

    private ChannelSyncMode syncMode;

    @NotBlank(message = "渠道来源链接不能为空")
    private String sourceUrl;

    private Long wecomConfigId;

    private Long productId;

    private String fieldMappingJson;

    private Integer syncIntervalMinutes;

    private boolean autoSync = true;

    private boolean autoAnalyze = true;

    private Long ownerId;
}
