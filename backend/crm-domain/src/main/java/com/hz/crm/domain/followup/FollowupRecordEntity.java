package com.hz.crm.domain.followup;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_followup_record")
@TableName("crm_followup_record")
public class FollowupRecordEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FollowupTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(length = 128)
    private String targetName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FollowupType followupType = FollowupType.PHONE;

    @Column(nullable = false)
    private LocalDateTime followupAt;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String result;

    @Column(columnDefinition = "text")
    private String nextPlan;

    private LocalDateTime nextFollowTime;

    private Long ownerId;
}
