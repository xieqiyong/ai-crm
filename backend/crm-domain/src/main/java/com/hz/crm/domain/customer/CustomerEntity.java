package com.hz.crm.domain.customer;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_customer")
@TableName("crm_customer")
public class CustomerEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 128)
    private String industry;

    @Column(length = 128)
    private String contactName;

    @Column(length = 32)
    private String contactPhone;

    @Column(length = 128)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustomerLevel level = CustomerLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'POTENTIAL'")
    private CustomerStatus status = CustomerStatus.recommended();

    private Long ownerId;

    @Column(length = 512)
    private String remark;
}
