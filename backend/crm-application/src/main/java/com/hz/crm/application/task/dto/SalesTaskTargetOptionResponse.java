package com.hz.crm.application.task.dto;

import com.hz.crm.domain.task.SalesTaskTargetType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTaskTargetOptionResponse {

    private Long id;

    private SalesTaskTargetType targetType;

    private String name;

    private String description;

    private Long ownerId;

    private String ownerName;
}
