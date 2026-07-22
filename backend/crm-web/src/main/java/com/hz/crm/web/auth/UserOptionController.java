package com.hz.crm.web.auth;

import com.hz.crm.auth.dto.UserOptionResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.auth.service.UserOptionService;
import com.hz.crm.common.api.ApiResult;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/users")
public class UserOptionController {

    @Autowired
    private UserOptionService userOptionService;

    @PostMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<UserOptionResponse>> options(JwtPrincipal principal) {
        return ApiResult.ok(userOptionService.list(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope()));
    }
}
