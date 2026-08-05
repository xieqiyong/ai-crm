package com.hz.crm.agent.web;

import com.hz.crm.agent.web.dto.AiRuntimeProxyRequest;
import com.hz.crm.agent.web.dto.AiRuntimeProxyResponse;
import com.hz.crm.agent.web.dto.CustomerDeepSummaryRequest;
import com.hz.crm.agent.web.dto.CustomerDeepSummaryResponse;
import com.hz.crm.agent.web.dto.LeadAiAnalyzeRequest;
import com.hz.crm.agent.web.dto.LeadAiAnalyzeResponse;
import com.hz.crm.agent.web.dto.MarketingAssistantChatRequest;
import com.hz.crm.agent.web.dto.MarketingAssistantChatResponse;
import com.hz.crm.agent.web.service.AiRuntimeProxyService;
import com.hz.crm.agent.web.service.CustomerDeepSummaryService;
import com.hz.crm.agent.web.service.LeadAiAssistantService;
import com.hz.crm.agent.web.service.MarketingAssistantChatService;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @Autowired
    private LeadAiAssistantService leadAiAssistantService;

    @Autowired
    private AiRuntimeProxyService aiRuntimeProxyService;

    @Autowired
    private MarketingAssistantChatService marketingAssistantChatService;

    @Autowired
    private CustomerDeepSummaryService customerDeepSummaryService;

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:assistant:use')")
    public ApiResult<MarketingAssistantChatResponse> chat(
            @RequestBody MarketingAssistantChatRequest request, JwtPrincipal principal) {
        return ApiResult.ok(marketingAssistantChatService.chat(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:assistant:use')")
    public SseEmitter chatStream(
            @RequestBody MarketingAssistantChatRequest request, JwtPrincipal principal) {
        return marketingAssistantChatService.chatStream(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request);
    }

    @PostMapping("/customer/deep-summary")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('crm:assistant:use') and hasAuthority('crm:customer:view'))")
    public ApiResult<CustomerDeepSummaryResponse> summarizeCustomer(
            @Valid @RequestBody CustomerDeepSummaryRequest request, JwtPrincipal principal) {
        return ApiResult.ok(customerDeepSummaryService.summarize(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/customer/deep-summary/status")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('crm:assistant:use') and hasAuthority('crm:customer:view'))")
    public ApiResult<CustomerDeepSummaryResponse> customerSummaryStatus(
            @Valid @RequestBody CustomerDeepSummaryRequest request, JwtPrincipal principal) {
        return ApiResult.ok(customerDeepSummaryService.status(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/lead/analyze")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('crm:assistant:use') "
            + "and (hasAuthority('crm:lead:view') or hasAuthority('crm:lead:manage')))")
    public ApiResult<LeadAiAnalyzeResponse> analyzeLead(
            @Valid @RequestBody LeadAiAnalyzeRequest request, JwtPrincipal principal) {
        return ApiResult.ok(leadAiAssistantService.analyze(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/langgraph/run")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<AiRuntimeProxyResponse> runLangGraph(
            @RequestBody AiRuntimeProxyRequest request, JwtPrincipal principal) {
        return ApiResult.ok(aiRuntimeProxyService.run(principal.getTenantId(), principal.getUserId(), request));
    }

    @PostMapping("/langgraph/lead/analyze")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('crm:assistant:use') "
            + "and (hasAuthority('crm:lead:view') or hasAuthority('crm:lead:manage')))")
    public ApiResult<AiRuntimeProxyResponse> analyzeLeadByLangGraph(
            @Valid @RequestBody LeadAiAnalyzeRequest request, JwtPrincipal principal) {
        return ApiResult.ok(aiRuntimeProxyService.analyzeLead(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }
}
