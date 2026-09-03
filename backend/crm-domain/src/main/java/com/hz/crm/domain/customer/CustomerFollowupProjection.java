package com.hz.crm.domain.customer;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerFollowupProjection {

    private Long customerId;

    private String customerName;

    private String contactName;

    private String contactPhone;

    private String productName;

    private Long ownerId;

    private String ownerName;

    private LocalDateTime createdAt;

    private LocalDateTime lastFollowupAt;

    private LocalDateTime nextFollowTime;
}
