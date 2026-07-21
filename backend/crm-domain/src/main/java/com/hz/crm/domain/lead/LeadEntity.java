package com.hz.crm.domain.lead;

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
    @Column(nullable = false, length = 32)
    private LeadStatus status = LeadStatus.NEW;

    private Long ownerId;

    @Column(length = 512)
    private String remark;
}
