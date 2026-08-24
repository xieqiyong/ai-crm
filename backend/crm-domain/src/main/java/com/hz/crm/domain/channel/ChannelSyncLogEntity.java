package com.hz.crm.domain.channel;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_channel_sync_log")
@TableName("crm_channel_sync_log")
public class ChannelSyncLogEntity extends BaseEntity {

    @Column(nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChannelSyncTrigger triggerType = ChannelSyncTrigger.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChannelSyncStatus status = ChannelSyncStatus.RUNNING;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Integer fetchedCount = 0;

    private Integer createdCount = 0;

    private Integer updatedCount = 0;

    private Integer skippedCount = 0;

    private Integer failedCount = 0;

    @Column(columnDefinition = "text")
    private String errorMessage;
}
