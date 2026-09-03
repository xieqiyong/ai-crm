package com.hz.crm.domain.lead;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadFollowupProjection {

    private Long leadId;

    private String leadName;

    private String companyName;

    private String phone;

    private String productName;

    private Long ownerId;

    private String ownerName;

    private LocalDateTime createdAt;

    private LocalDateTime lastFollowupAt;

    private LocalDateTime nextFollowTime;
}
