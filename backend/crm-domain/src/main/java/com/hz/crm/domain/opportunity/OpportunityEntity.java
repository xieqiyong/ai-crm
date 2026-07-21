package com.hz.crm.domain.opportunity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_opportunity")
@TableName("crm_opportunity")
public class OpportunityEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    private Long customerId;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OpportunityStage stage = OpportunityStage.DISCOVERY;

    private Integer probability;

    private LocalDate expectedCloseDate;

    private Long ownerId;

    @Column(length = 512)
    private String remark;
}
