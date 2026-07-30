package com.hz.crm.web.notification;

import com.hz.crm.auth.dto.notification.NotificationIdRequest;
import com.hz.crm.auth.dto.notification.NotificationItemResponse;
import com.hz.crm.auth.dto.notification.NotificationQuery;
import com.hz.crm.auth.dto.notification.NotificationSendRequest;
import com.hz.crm.auth.dto.notification.NotificationUnreadResponse;
import com.hz.crm.auth.dto.UserOptionResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.auth.service.NotificationService;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.audit.AuditOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/page")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<PageData<NotificationItemResponse>> page(
            @RequestBody(required = false) NotificationQuery query, JwtPrincipal principal) {
        return ApiResult.ok(notificationService.page(
                principal.getTenantId(), principal.getUserId(), query));
    }

    @PostMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<NotificationUnreadResponse> unreadCount(JwtPrincipal principal) {
        return ApiResult.ok(notificationService.unreadCount(
                principal.getTenantId(), principal.getUserId()));
    }

    @PostMapping("/recipients")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:notification:manage')")
    public ApiResult<List<UserOptionResponse>> recipients(JwtPrincipal principal) {
        return ApiResult.ok(notificationService.recipients(principal.getTenantId()));
    }

    @PostMapping("/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<Void> read(@RequestBody NotificationIdRequest request, JwtPrincipal principal) {
        notificationService.read(principal.getTenantId(), principal.getUserId(), request.getId());
        return ApiResult.ok(null);
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<Void> readAll(JwtPrincipal principal) {
        notificationService.readAll(principal.getTenantId(), principal.getUserId());
        return ApiResult.ok(null);
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:notification:manage')")
    @AuditOperation(
            module = "NOTIFICATION",
            action = "SEND",
            description = "发布系统通知",
            targetType = "NOTIFICATION")
    public ApiResult<Void> send(
            @RequestBody NotificationSendRequest request, JwtPrincipal principal) {
        notificationService.send(principal.getTenantId(), principal.getUserId(), request);
        return ApiResult.ok(null);
    }
}
