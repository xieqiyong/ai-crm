package com.hz.crm.agent.web.dto;

import com.hz.crm.application.lead.dto.LeadResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadAiAnalyzeResponse {

    private Long leadId;

    private Long runId;

    private Long conversationId;

    private String leadName;

    private boolean available;

    private boolean success;

    private String message;

    private String summary;

    private String conclusionTitle;

    private String salesConclusion;

    private String stage;

    private String priority;

    private Boolean recommendConvert;

    private Integer score;

    private BigDecimal confidence;

    private String reason;

    private String nextAction;

    private List<String> keyFindings = new ArrayList<String>();

    private List<String> nextActions = new ArrayList<String>();

    private List<String> riskWarnings = new ArrayList<String>();

    private LeadAiConvertDraft convertDraft;

    private LeadAiCustomerProfile customerProfile;

    private String rawOutput;

    private LeadResponse lead;
}
