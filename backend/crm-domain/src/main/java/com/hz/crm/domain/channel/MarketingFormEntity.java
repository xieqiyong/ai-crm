package com.hz.crm.domain.channel;

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
@Table(name = "crm_marketing_form")
@TableName("crm_marketing_form")
public class MarketingFormEntity extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String formCode;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 512)
    private String description;

    @Column(length = 128)
    private String source;

    @Column(length = 256)
    private String submitMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarketingFormStatus status = MarketingFormStatus.DRAFT;

    @Column(nullable = false)
    private boolean autoCreateLead;

    private Long ownerId;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long viewCount = 0L;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long submitCount = 0L;
}
