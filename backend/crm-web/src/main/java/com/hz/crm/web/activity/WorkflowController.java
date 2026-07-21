package com.hz.crm.web.activity;

import com.hz.crm.activity.dto.WorkflowStartRequest;
import com.hz.crm.activity.service.WorkflowService;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.web.support.WebUserSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WebUserSupport webUserSupport;

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:workflow:manage')")
    public ApiResult<Long> start(@RequestBody WorkflowStartRequest request, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        Long instanceId = workflowService.start(
                principal.getTenantId(), request.getDefinitionCode(), request.getBusinessType(), request.getBusinessId());
        return ApiResult.ok(instanceId);
    }
}
