package com.hz.crm.domain.wecom;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "wecom_sync_task",
        indexes = {
            @Index(
                    name = "idx_wecom_sync_task_latest",
                    columnList = "tenant_id,config_id,created_at")
        })
@TableName("wecom_sync_task")
public class WecomSyncTaskEntity extends BaseEntity {

    @Column(nullable = false)
    private Long configId;

    private Long operatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private WecomSyncTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private WecomSyncStatus status;

    private Integer contactsFetched = 0;

    private Integer contactsCreated = 0;

    private Integer contactsUpdated = 0;

    private Integer groupsFetched = 0;

    private Integer groupMembersFetched = 0;

    private Integer channelsCreated = 0;

    private Integer channelsUpdated = 0;

    private Integer duplicatesSkipped = 0;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(columnDefinition = "text")
    private String errorMessage;
}
