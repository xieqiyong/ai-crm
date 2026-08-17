package com.hz.crm.domain.system;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "crm_system_parameter",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_system_parameter_key", columnNames = {"tenant_id", "param_key"})
        },
        indexes = {
                @Index(name = "idx_system_parameter_group", columnList = "tenant_id,group_code")
        })
@TableName("crm_system_parameter")
public class SystemParameterEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String paramKey;

    @Column(nullable = false, columnDefinition = "text")
    private String paramValue;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(nullable = false, length = 64)
    private String groupCode;

    @Column(nullable = false, length = 32)
    private String valueType;

    @Column(nullable = false)
    private Integer sortNo = Integer.valueOf(0);
}
