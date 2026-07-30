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
        name = "wecom_contact_follow",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_wecom_contact_follow",
                    columnNames = {"tenant_id", "config_id", "external_user_id", "wecom_user_id"})
        })
@TableName("wecom_contact_follow")
public class WecomContactFollowEntity extends BaseEntity {

    @Column(nullable = false)
    private Long configId;

    @Column(nullable = false)
    private Long externalContactId;

    @Column(nullable = false, length = 128)
    private String externalUserId;

    @Column(nullable = false, length = 128)
    private String wecomUserId;

    private Long ownerId;

    @Column(length = 128)
    private String remark;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 128)
    private String state;

    private Integer addWay;

    private LocalDateTime contactCreatedAt;

    @Column(length = 256)
    private String remarkCorpName;

    @Column(columnDefinition = "text")
    private String mobilesJson;

    @Column(columnDefinition = "text")
    private String tagsJson;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime firstSyncedAt;

    private LocalDateTime lastSyncedAt;
}
