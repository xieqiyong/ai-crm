package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.ChannelSourceKind;
import com.hz.crm.domain.channel.ChannelSourceStatus;
import com.hz.crm.domain.channel.ChannelSyncMode;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSourceResponse {

    private Long id;

    private Long tenantId;

    private String name;

    private ChannelSourceKind sourceType;

    private ChannelSourceStatus status;

    private ChannelSyncMode syncMode;

    private String sourceUrl;

    private String externalProvider;

    private String externalKey;

    private Long wecomConfigId;

    private Long productId;

    private String productName;

    private String docId;

    private String sheetId;

    private String viewId;

    private String fieldMappingJson;

    private Integer syncIntervalMinutes;

    private boolean autoSync;

    private boolean autoAnalyze;

    private Long ownerId;

    private String ownerName;

    private LocalDateTime lastSyncAt;

    private LocalDateTime lastSuccessAt;

    private String lastError;

    private Long totalRecordCount;

    private Long todayNewCount;

    private Long convertedLeadCount;

    private Long duplicateCount;

    private Long failedCount;

    private String latestFieldSnapshot;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
