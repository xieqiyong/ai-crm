package com.hz.crm.domain.channel;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_marketing_form_submission")
@TableName("crm_marketing_form_submission")
public class MarketingFormSubmissionEntity extends BaseEntity {

    @Column(nullable = false)
    private Long formId;

    @Column(nullable = false, length = 64)
    private String formCode;

    private Long channelId;

    private Long leadId;

    @Column(length = 128)
    private String contactName;

    @Column(length = 128)
    private String companyName;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(length = 64)
    private String visitorIp;

    @Column(length = 256)
    private String userAgent;

    @Column(columnDefinition = "text")
    private String payloadJson;
}
