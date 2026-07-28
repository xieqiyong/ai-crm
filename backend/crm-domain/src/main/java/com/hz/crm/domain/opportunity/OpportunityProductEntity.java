package com.hz.crm.domain.opportunity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import com.hz.crm.domain.product.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_opportunity_product")
@TableName("crm_opportunity_product")
public class OpportunityProductEntity extends BaseEntity {

    @Column(nullable = false)
    private Long opportunityId;

    private Long productId;

    @Column(length = 64)
    private String productCode;

    @Column(nullable = false, length = 128)
    private String productName;

    @Column(length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ProductType productType;

    @Column(precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 8, scale = 2)
    private BigDecimal discountRate;

    @Column(precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(length = 32)
    private String unit;

    @Column(length = 512)
    private String remark;
}
