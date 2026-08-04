package com.hz.crm.application.task.dto;

import com.hz.crm.domain.task.SalesTaskTargetType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTaskTargetOptionQuery {

    private SalesTaskTargetType targetType;

    private String keyword;

    private Integer limit;
}
