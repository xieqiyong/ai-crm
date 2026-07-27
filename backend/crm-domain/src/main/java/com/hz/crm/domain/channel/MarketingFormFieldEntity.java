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
@Table(name = "crm_marketing_form_field")
@TableName("crm_marketing_form_field")
public class MarketingFormFieldEntity extends BaseEntity {

    @Column(nullable = false)
    private Long formId;

    @Column(nullable = false, length = 64)
    private String fieldKey;

    @Column(nullable = false, length = 64)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarketingFormFieldType fieldType = MarketingFormFieldType.TEXT;

    @Column(nullable = false)
    private boolean requiredField;

    @Column(length = 128)
    private String placeholder;

    @Column(columnDefinition = "text")
    private String optionsText;

    @Column(length = 64)
    private String systemMapping;

    private Integer sortOrder;
}
