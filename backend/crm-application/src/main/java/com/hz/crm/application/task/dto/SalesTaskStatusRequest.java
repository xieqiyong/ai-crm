package com.hz.crm.application.task.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTaskStatusRequest {

    private Long id;

    private String cancelReason;
}
