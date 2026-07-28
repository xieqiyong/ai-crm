package com.hz.crm.application.followup.dto;

import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.followup.FollowupType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupSaveRequest {

    private Long id;

    @NotNull(message = "关联对象类型不能为空")
    private FollowupTargetType targetType;

    @NotNull(message = "关联对象不能为空")
    private Long targetId;

    private FollowupType followupType;

    private LocalDateTime followupAt;

    @NotBlank(message = "跟进内容不能为空")
    private String content;

    private String result;

    private String nextPlan;

    private LocalDateTime nextFollowTime;

    private Long ownerId;
}
