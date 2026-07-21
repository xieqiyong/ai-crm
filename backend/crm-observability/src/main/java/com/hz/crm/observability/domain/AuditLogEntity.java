package com.hz.crm.observability.domain;

import com.hz.crm.common.time.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "obs_audit_log")
public class AuditLogEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 64)
    private String tenantId;

    private Long operatorId;

    @Column(nullable = false, length = 128)
    private String action;

    @Column(length = 128)
    private String targetType;

    private Long targetId;

    @Lob
    private String detailJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimes.now();
        }
    }
}
