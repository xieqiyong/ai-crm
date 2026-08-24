package com.hz.crm.domain.lead;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_lead")
@TableName("crm_lead")
public class LeadEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 128)
    private String companyName;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(length = 64)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'NEW'")
    private LeadStatus status = LeadStatus.recommended();

    private Long customerId;

    private LocalDateTime convertedAt;

    private Long convertedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private LeadConvertType convertedType;

    @Column(columnDefinition = "text")
    private String aiSummary;

    @Column(length = 128)
    private String aiSuggestedCustomerName;

    @Column(length = 128)
    private String aiSuggestedContactName;

    private BigDecimal aiConfidence;

    private LocalDateTime aiAnalyzedAt;

    private Long ownerId;

    private Long productId;

    @Column(columnDefinition = "text")
    private String remark;
}
