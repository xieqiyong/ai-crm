package com.hz.crm.domain.product;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
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
@Table(name = "crm_product")
@TableName("crm_product")
public class ProductEntity extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private ProductCategory category = ProductCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductType productType = ProductType.STANDARD;

    @Column(precision = 18, scale = 2)
    private BigDecimal price;

    @Column(length = 32)
    private String unit;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 512)
    private String remark;
}
