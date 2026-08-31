package com.hz.crm.domain.channel;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "crm_channel_source",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_channel_source_external",
                    columnNames = {"tenant_id", "external_provider", "external_key"})
        })
@TableName("crm_channel_source")
public class ChannelSourceEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChannelSourceKind sourceType = ChannelSourceKind.WECOM_SMART_SHEET;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChannelSourceStatus status = ChannelSourceStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChannelSyncMode syncMode = ChannelSyncMode.SCHEDULED;

    @Column(columnDefinition = "text")
    private String sourceUrl;

    @Column(length = 32)
    private String externalProvider;

    @Column(length = 512)
    private String externalKey;

    private Long wecomConfigId;

    @Column(nullable = false)
    private Long productId;

    @Column(length = 128)
    private String docId;

    @Column(length = 64)
    private String sheetId;

    @Column(length = 64)
    private String viewId;

    @Column(columnDefinition = "text")
    private String fieldMappingJson;

    @Column(nullable = false)
    private Integer syncIntervalMinutes = 10;

    @Column(nullable = false)
    private boolean autoSync = true;

    @Column(nullable = false)
    private boolean autoAnalyze = true;

    private Long ownerId;

    private LocalDateTime lastSyncAt;

    private LocalDateTime lastSuccessAt;

    @Column(columnDefinition = "text")
    private String lastError;

    private Long totalRecordCount = 0L;

    private Long todayNewCount = 0L;

    private Long convertedLeadCount = 0L;

    private Long duplicateCount = 0L;

    private Long failedCount = 0L;

    @Column(columnDefinition = "text")
    private String latestFieldSnapshot;
}
