package com.hz.crm.application.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupTaskSettingsSaveRequest {

    @NotNull(message = "第一次提醒间隔不能为空")
    @Min(value = 1, message = "第一次提醒间隔不能小于1分钟")
    @Max(value = 525600, message = "第一次提醒间隔不能大于525600分钟")
    private Integer firstDelayMinutes;

    @NotNull(message = "第二次提醒间隔不能为空")
    @Min(value = 1, message = "第二次提醒间隔不能小于1分钟")
    @Max(value = 525600, message = "第二次提醒间隔不能大于525600分钟")
    private Integer secondDelayMinutes;
}
