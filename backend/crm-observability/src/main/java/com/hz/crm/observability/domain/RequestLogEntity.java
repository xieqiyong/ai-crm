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
@Table(name = "obs_request_log")
public class RequestLogEntity {

    @Id
    private Long id;

    @Column(columnDefinition = "bigint")
    private Long tenantId;

    private Long operatorId;

    @Column(length = 64)
    private String username;

    @Column(nullable = false, length = 64)
    private String traceId;

    @Column(nullable = false, length = 16)
    private String requestMethod;

    @Column(nullable = false, length = 512)
    private String requestUri;

    @Column(length = 64)
    private String clientIp;

    @Column(length = 512)
    private String userAgent;

    private Integer statusCode;

    private Long costMillis;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 64)
    private String errorCode;

    @Lob
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimes.now();
        }
    }
}
