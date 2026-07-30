package com.hz.crm.domain.wecom;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "wecom_corp_config",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_wecom_corp_config", columnNames = {"tenant_id", "corp_id"})
        })
@TableName("wecom_corp_config")
public class WecomCorpConfigEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 128)
    private String corpId;

    @Column(nullable = false, length = 512)
    private String corpSecret;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Integer syncIntervalMinutes = 10;

    private Long defaultOwnerId;

    @Column(length = 32)
    private String lastSyncStatus;

    private LocalDateTime lastSyncAt;

    private LocalDateTime lastSuccessAt;

    @Column(columnDefinition = "text")
    private String lastError;
}
