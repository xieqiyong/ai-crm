package com.hz.crm.domain.wecom;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "wecom_user_binding",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_wecom_user_binding",
                    columnNames = {"tenant_id", "config_id", "wecom_user_id"})
        })
@TableName("wecom_user_binding")
public class WecomUserBindingEntity extends BaseEntity {

    @Column(nullable = false)
    private Long configId;

    @Column(nullable = false, length = 128)
    private String wecomUserId;

    @Column(length = 128)
    private String wecomUserName;

    private Long crmUserId;

    @Column(nullable = false)
    private boolean enabled = true;
}
