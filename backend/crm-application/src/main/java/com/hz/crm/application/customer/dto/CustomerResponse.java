package com.hz.crm.application.customer.dto;

import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.CustomerStatus;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerResponse {

    private Long id;

    private Long tenantId;

    private String name;

    private String industry;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private CustomerLevel level;

    private CustomerStatus status;

    private Long ownerId;

    private String ownerName;

    private String remark;

    private String aiSummary;

    private LocalDateTime aiAnalyzedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
