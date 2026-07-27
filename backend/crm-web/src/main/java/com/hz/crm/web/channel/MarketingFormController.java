package com.hz.crm.web.channel;

import com.hz.crm.application.channel.MarketingFormApplicationService;
import com.hz.crm.application.channel.dto.MarketingFormCodeRequest;
import com.hz.crm.application.channel.dto.MarketingFormQuery;
import com.hz.crm.application.channel.dto.MarketingFormResponse;
import com.hz.crm.application.channel.dto.MarketingFormSaveRequest;
import com.hz.crm.application.channel.dto.PublicMarketingFormResponse;
import com.hz.crm.application.channel.dto.PublicMarketingFormSubmitRequest;
import com.hz.crm.application.channel.dto.PublicMarketingFormSubmitResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.web.support.IdRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarketingFormController {

    @Autowired
    private MarketingFormApplicationService marketingFormApplicationService;

    @PostMapping("/api/channel/marketing-form/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:view')")
    public ApiResult<PageData<MarketingFormResponse>> page(
            @RequestBody(required = false) MarketingFormQuery query, JwtPrincipal principal) {
        return ApiResult.ok(marketingFormApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/api/channel/marketing-form/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:view')")
    public ApiResult<MarketingFormResponse> detail(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(marketingFormApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/api/channel/marketing-form/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:manage')")
    public ApiResult<MarketingFormResponse> save(
            @RequestBody MarketingFormSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(marketingFormApplicationService.save(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/api/channel/marketing-form/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:manage')")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        marketingFormApplicationService.delete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId());
        return ApiResult.ok(null);
    }

    @PostMapping("/api/public/marketing-form/detail")
    public ApiResult<PublicMarketingFormResponse> publicDetail(@RequestBody MarketingFormCodeRequest request) {
        return ApiResult.ok(marketingFormApplicationService.publicDetail(
                request == null ? null : request.getFormCode()));
    }

    @PostMapping("/api/public/marketing-form/submit")
    public ApiResult<PublicMarketingFormSubmitResponse> submit(
            @RequestBody PublicMarketingFormSubmitRequest request, HttpServletRequest servletRequest) {
        return ApiResult.ok(marketingFormApplicationService.submit(
                request, resolveClientIp(servletRequest), servletRequest.getHeader("User-Agent")));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(value)) {
            return value.split(",")[0].trim();
        }
        value = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return request.getRemoteAddr();
    }
}
