package com.hz.crm.web.auth;

import com.hz.crm.auth.dto.CurrentUserResponse;
import com.hz.crm.auth.dto.ForgotPasswordRequest;
import com.hz.crm.auth.dto.LoginRequest;
import com.hz.crm.auth.dto.LoginResponse;
import com.hz.crm.auth.dto.PasswordResetResponse;
import com.hz.crm.auth.dto.ResetPasswordRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.auth.service.AuthApplicationService;
import com.hz.crm.common.api.ApiResult;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResult.ok(authApplicationService.login(request));
    }

    @PostMapping("/forgot-password")
    public ApiResult<PasswordResetResponse> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        return ApiResult.ok(authApplicationService.requestPasswordReset(request));
    }

    @PostMapping("/reset-password")
    public ApiResult<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        authApplicationService.resetPassword(request);
        return ApiResult.ok(null);
    }

    @GetMapping("/me")
    public ApiResult<CurrentUserResponse> me(JwtPrincipal principal) {
        return ApiResult.ok(toResponse(principal));
    }

    @PostMapping("/me")
    public ApiResult<CurrentUserResponse> mePost(JwtPrincipal principal) {
        return ApiResult.ok(toResponse(principal));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(JwtPrincipal principal) {
        authApplicationService.logout(principal);
        return ApiResult.ok(null);
    }

    private CurrentUserResponse toResponse(JwtPrincipal principal) {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setUserId(principal.getUserId());
        response.setTenantId(principal.getTenantId());
        response.setUsername(principal.getUsername());
        response.setDisplayName(principal.getDisplayName());
        response.setSessionId(principal.getSessionId());
        response.setExpiresAt(principal.getExpiresAt());
        response.setTtlSeconds(principal.getTtlSeconds());
        response.setPermissions(new ArrayList<String>(principal.getPermissions()));
        response.setMenuPermissions(new ArrayList<String>(principal.getMenuPermissions()));
        response.setDataPermissions(new ArrayList<String>(principal.getDataPermissions()));
        response.setDataScope(principal.getDataScope());
        return response;
    }
}
