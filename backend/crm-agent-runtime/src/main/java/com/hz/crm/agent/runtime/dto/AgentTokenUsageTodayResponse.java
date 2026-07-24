package com.hz.crm.agent.runtime.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTokenUsageTodayResponse {

    private LocalDate usageDate;

    private Long dailyTokenLimit = 0L;

    private String quotaSource = "DEFAULT";

    private Long inputTokenCount = 0L;

    private Long outputTokenCount = 0L;

    private Long totalTokenCount = 0L;

    private Long estimatedTokenCount = 0L;

    private Long reservedTokenCount = 0L;

    private Long remainingTokenCount = 0L;

    private Long requestCount = 0L;

    private Long successCount = 0L;

    private Long failedCount = 0L;
}
