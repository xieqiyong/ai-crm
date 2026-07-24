package com.hz.crm.agent.runtime.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTokenQuotaUserResponse {

    private Long id;

    private Long userId;

    private String username;

    private String displayName;

    private Long departmentId;

    private String departmentName;

    private Long dailyTokenLimit = 0L;

    private String assignScope;

    private Long assignTargetId;

    private String assignTargetName;

    private String remark;

    private boolean enabled;

    private LocalDateTime updatedAt;
}
