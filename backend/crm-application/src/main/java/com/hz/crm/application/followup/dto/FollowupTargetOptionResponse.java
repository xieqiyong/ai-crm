package com.hz.crm.application.followup.dto;

import com.hz.crm.domain.followup.FollowupTargetType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupTargetOptionResponse {

    private Long id;

    private FollowupTargetType targetType;

    private String name;

    private String description;

    private Long ownerId;

    private String ownerName;
}
