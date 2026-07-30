package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.ChannelType;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelResponse {

    private Long id;

    private Long tenantId;

    private String title;

    private ChannelType channelType;

    private ChannelStatus status;

    private String source;

    private String contactName;

    private String companyName;

    private String phone;

    private String email;

    private String mediaFileName;

    private String mediaContentType;

    private Long mediaSize;

    private String mediaStorageKey;

    private String transcriptText;

    private String aiSummary;

    private String usefulInfo;

    private String aiAnalysisJson;

    private Long agentRunId;

    private LocalDateTime aiAnalyzedAt;

    private Long leadId;

    private Long ownerId;

    private String ownerName;

    private String remark;

    private String externalProvider;

    private String externalKey;

    private String externalVersion;

    private String sourceSnapshot;

    private boolean promotionReady;

    private String promotionBlockReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
