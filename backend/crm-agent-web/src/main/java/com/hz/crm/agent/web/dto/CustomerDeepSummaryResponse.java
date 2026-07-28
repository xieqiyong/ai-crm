package com.hz.crm.agent.web.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerDeepSummaryResponse {

    private Long customerId;

    private Long runId;

    private Long conversationId;

    private boolean available;

    private boolean success;

    private String message;

    private String summary;

    private List<String> keyFindings = new ArrayList<String>();

    private List<String> risks = new ArrayList<String>();

    private List<String> nextActions = new ArrayList<String>();

    private int opportunityCount;

    private int followupCount;

    private LocalDateTime analyzedAt;
}
