package com.hz.crm.web.knowledge;

import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.api.PageQuery;
import com.hz.crm.knowledge.domain.KnowledgeDocumentEntity;
import com.hz.crm.knowledge.dto.KnowledgeDocumentRequest;
import com.hz.crm.knowledge.service.KnowledgeDocumentService;
import com.hz.crm.web.support.WebUserSupport;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/document")
public class KnowledgeDocumentController {

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @Autowired
    private WebUserSupport webUserSupport;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<PageData<KnowledgeDocumentEntity>> page(PageQuery query, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(knowledgeDocumentService.page(principal.getTenantId(), query));
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<PageData<KnowledgeDocumentEntity>> pagePost(
            @RequestBody(required = false) PageQuery query, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(knowledgeDocumentService.page(principal.getTenantId(), query));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<KnowledgeDocumentEntity> save(
            @Valid @RequestBody KnowledgeDocumentRequest request, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(knowledgeDocumentService.save(principal.getTenantId(), request));
    }
}
