package com.hz.crm.web.auth;

import com.hz.crm.auth.dto.CurrentUserResponse;
import com.hz.crm.auth.dto.LoginRequest;
import com.hz.crm.auth.dto.LoginResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.auth.service.AuthApplicationService;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.web.support.WebUserSupport;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthApplicationService authApplicationService;

    @Autowired
    private WebUserSupport webUserSupport;

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResult.ok(authApplicationService.login(request));
    }

    @GetMapping("/me")
    public ApiResult<CurrentUserResponse> me(Authentication authentication) {
        return ApiResult.ok(toResponse(webUserSupport.current(authentication)));
    }

    @PostMapping("/me")
    public ApiResult<CurrentUserResponse> mePost(Authentication authentication) {
        return ApiResult.ok(toResponse(webUserSupport.current(authentication)));
    }

    private CurrentUserResponse toResponse(JwtPrincipal principal) {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setUserId(principal.getUserId());
        response.setTenantId(principal.getTenantId());
        response.setUsername(principal.getUsername());
        response.setDisplayName(principal.getDisplayName());
        response.setPermissions(new ArrayList<String>(principal.getPermissions()));
        response.setMenuPermissions(new ArrayList<String>(principal.getMenuPermissions()));
        response.setDataPermissions(new ArrayList<String>(principal.getDataPermissions()));
        response.setDataScope(principal.getDataScope());
        return response;
    }
}
