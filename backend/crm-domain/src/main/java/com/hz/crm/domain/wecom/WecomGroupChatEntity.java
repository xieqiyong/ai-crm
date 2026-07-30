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
        name = "wecom_group_chat",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_wecom_group_chat",
                    columnNames = {"tenant_id", "config_id", "chat_id"})
        })
@TableName("wecom_group_chat")
public class WecomGroupChatEntity extends BaseEntity {

    @Column(nullable = false)
    private Long configId;

    @Column(nullable = false, length = 128)
    private String chatId;

    @Column(length = 256)
    private String name;

    @Column(length = 128)
    private String ownerUserId;

    @Column(columnDefinition = "text")
    private String notice;

    private Integer chatStatus;

    private LocalDateTime groupCreatedAt;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime lastSyncedAt;
}
