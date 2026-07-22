package com.hz.crm.application.lead.dto;

import com.hz.crm.domain.lead.LeadStatus;
import com.hz.crm.domain.lead.LeadConvertType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadResponse {

    private Long id;

    private String tenantId;

    private String name;

    private String companyName;

    private String phone;

    private String email;

    private String source;

    private LeadStatus status;

    private Long customerId;

    private String customerName;

    private LocalDateTime convertedAt;

    private Long convertedBy;

    private String convertedByName;

    private LeadConvertType convertedType;

    private String aiSummary;

    private String aiSuggestedCustomerName;

    private String aiSuggestedContactName;

    private BigDecimal aiConfidence;

    private LocalDateTime aiAnalyzedAt;

    private Long ownerId;

    private String ownerName;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
