package com.hz.crm.domain.wecom;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "wecom_external_contact",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_wecom_external_contact",
                    columnNames = {"tenant_id", "config_id", "external_user_id"})
        })
@TableName("wecom_external_contact")
public class WecomExternalContactEntity extends BaseEntity {

    @Column(nullable = false)
    private Long configId;

    @Column(nullable = false, length = 128)
    private String externalUserId;

    @Column(length = 128)
    private String name;

    private Integer contactType;

    private Integer gender;

    @Column(length = 512)
    private String avatar;

    @Column(length = 128)
    private String position;

    @Column(length = 256)
    private String corpName;

    @Column(length = 512)
    private String corpFullName;

    @Column(length = 128)
    private String unionId;

    @Column(columnDefinition = "text")
    private String profileJson;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime firstSyncedAt;

    private LocalDateTime lastSyncedAt;
}
