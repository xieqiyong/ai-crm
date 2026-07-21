package com.hz.crm.web.auth;

import com.hz.crm.auth.dto.InstallStatusResponse;
import com.hz.crm.auth.dto.LoginResponse;
import com.hz.crm.auth.dto.SetupSuperAdminRequest;
import com.hz.crm.auth.service.InstallationService;
import com.hz.crm.common.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/install")
public class InstallController {

    @Autowired
    private InstallationService installationService;

    @GetMapping("/status")
    public ApiResult<InstallStatusResponse> status() {
        return ApiResult.ok(installationService.status());
    }

    @PostMapping("/status")
    public ApiResult<InstallStatusResponse> statusPost() {
        return ApiResult.ok(installationService.status());
    }

    @PostMapping("/setup")
    public ApiResult<LoginResponse> setup(@Valid @RequestBody SetupSuperAdminRequest request) {
        return ApiResult.ok(installationService.setup(request));
    }
}
