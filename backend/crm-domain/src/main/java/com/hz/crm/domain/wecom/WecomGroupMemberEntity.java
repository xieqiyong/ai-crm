package com.hz.crm.domain.wecom;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "wecom_group_member",
        indexes = {
            @Index(
                    name = "idx_wecom_group_member_external",
                    columnList = "tenant_id,config_id,member_user_id")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_wecom_group_member",
                    columnNames = {"tenant_id", "config_id", "chat_id", "member_user_id"})
        })
@TableName("wecom_group_member")
public class WecomGroupMemberEntity extends BaseEntity {

    @Column(nullable = false)
    private Long configId;

    @Column(nullable = false)
    private Long groupChatId;

    @Column(nullable = false, length = 128)
    private String chatId;

    @Column(nullable = false, length = 128)
    private String memberUserId;

    private Integer memberType;

    @Column(length = 128)
    private String name;

    @Column(length = 128)
    private String unionId;

    private LocalDateTime joinedAt;

    private Integer joinScene;

    @Column(length = 128)
    private String inviterUserId;

    @Column(length = 128)
    private String groupNickname;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime firstSyncedAt;

    private LocalDateTime lastSyncedAt;
}
