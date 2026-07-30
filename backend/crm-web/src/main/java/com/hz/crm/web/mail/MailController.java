package com.hz.crm.web.mail;

import com.hz.crm.application.mail.MailApplicationService;
import com.hz.crm.application.mail.dto.MailAccountResponse;
import com.hz.crm.application.mail.dto.MailAccountSaveRequest;
import com.hz.crm.application.mail.dto.MailAttachment;
import com.hz.crm.application.mail.dto.MailLogQuery;
import com.hz.crm.application.mail.dto.MailSendRequest;
import com.hz.crm.application.mail.dto.MailCustomerOptionResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.mail.MailSendLogEntity;
import com.hz.crm.web.support.IdRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tools/mail")
public class MailController {

    @Autowired
    private MailApplicationService mailApplicationService;

    @PostMapping("/account/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:mail:config')")
    public ApiResult<MailAccountResponse> account(JwtPrincipal principal) {
        return ApiResult.ok(mailApplicationService.account(principal.getTenantId()));
    }

    @PostMapping("/account/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:mail:config')")
    @AuditOperation(
            module = "MAIL",
            action = "SAVE_ACCOUNT",
            description = "保存邮件发件配置",
            targetType = "MAIL_ACCOUNT",
            recordParameters = false)
    public ApiResult<MailAccountResponse> saveAccount(
            @RequestBody MailAccountSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(mailApplicationService.saveAccount(principal.getTenantId(), request));
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:mail:send')")
    @AuditOperation(
            module = "MAIL",
            action = "SEND",
            description = "向客户发送邮件",
            targetType = "CUSTOMER",
            targetIdField = "customerId",
            recordParameters = false)
    public ApiResult<Void> send(
            @RequestPart("request") MailSendRequest request,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            JwtPrincipal principal) {
        mailApplicationService.send(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getDataScope(),
                request,
                toAttachments(files));
        return ApiResult.ok(null);
    }

    @PostMapping("/logs/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:mail:view')")
    public ApiResult<PageData<MailSendLogEntity>> pageLogs(
            @RequestBody(required = false) MailLogQuery query, JwtPrincipal principal) {
        return ApiResult.ok(mailApplicationService.pageLogs(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getDataScope(),
                query));
    }

    @PostMapping("/logs/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:mail:delete')")
    @AuditOperation(
            module = "MAIL",
            action = "DELETE_LOG",
            description = "删除邮件发送记录",
            targetType = "MAIL_SEND_LOG")
    public ApiResult<Void> deleteLog(
            @RequestBody IdRequest request, JwtPrincipal principal) {
        mailApplicationService.deleteLog(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getDataScope(),
                request.getId());
        return ApiResult.ok(null);
    }

    @PostMapping("/customers/options")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:mail:send')")
    public ApiResult<List<MailCustomerOptionResponse>> customerOptions(JwtPrincipal principal) {
        return ApiResult.ok(mailApplicationService.customerOptions(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getDataScope()));
    }

    private List<MailAttachment> toAttachments(MultipartFile[] files) {
        List<MailAttachment> attachments = new ArrayList<MailAttachment>();
        if (files == null) {
            return attachments;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                MailAttachment attachment = new MailAttachment();
                attachment.setName(file.getOriginalFilename());
                attachment.setContentType(file.getContentType());
                attachment.setContent(file.getBytes());
                attachments.add(attachment);
            } catch (IOException ex) {
                throw new BusinessException("MAIL_ATTACHMENT_005", "读取邮件附件失败");
            }
        }
        return attachments;
    }
}
