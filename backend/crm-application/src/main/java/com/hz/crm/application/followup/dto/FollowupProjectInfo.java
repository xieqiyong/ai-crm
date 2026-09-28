package com.hz.crm.application.followup.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupProjectInfo {

    @Size(max = 4000, message = "项目背景不能超过4000个字符")
    private String background;

    @Size(max = 4000, message = "核心需求不能超过4000个字符")
    private String requirements;

    @Size(max = 32, message = "项目阶段格式不正确")
    private String projectStage;

    @Size(max = 32, message = "预算状态格式不正确")
    private String budgetStatus;

    @Size(max = 32, message = "招投标状态格式不正确")
    private String tenderStatus;

    @Size(max = 32, message = "竞争状态格式不正确")
    private String competitionStatus;

    @Size(max = 1000, message = "竞争对手不能超过1000个字符")
    private String competitors;

    @Size(max = 32, message = "预计采购时间格式不正确")
    private String expectedPurchaseDate;
}
