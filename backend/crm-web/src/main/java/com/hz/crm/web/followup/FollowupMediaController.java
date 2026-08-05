package com.hz.crm.web.followup;

import com.hz.crm.application.media.MediaTranscriptionApplicationService;
import com.hz.crm.application.media.dto.MediaTranscriptionCreateRequest;
import com.hz.crm.application.media.dto.MediaTranscriptionResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.web.attachment.AttachmentStorageService;
import com.hz.crm.web.attachment.AttachmentUploadResponse;
import com.hz.crm.web.support.IdRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/followup/media")
public class FollowupMediaController {

    @Autowired
    private AttachmentStorageService attachmentStorageService;

    @Autowired
    private MediaTranscriptionApplicationService mediaTranscriptionApplicationService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:followup:manage') or hasAuthority('crm:followup:create')")
    @AuditOperation(
            module = "FOLLOWUP",
            action = "UPLOAD_MEDIA",
            description = "上传跟进音视频",
            targetType = "FOLLOWUP")
    public ApiResult<MediaTranscriptionResponse> upload(
            @RequestParam("followupId") Long followupId,
            @RequestParam("file") MultipartFile file,
            JwtPrincipal principal) {
        AttachmentUploadResponse attachment = attachmentStorageService.uploadMedia(file);
        MediaTranscriptionCreateRequest request = new MediaTranscriptionCreateRequest();
        request.setBusinessId(followupId);
        request.setFileName(attachment.getFileName());
        request.setContentType(attachment.getContentType());
        request.setFileSize(attachment.getSize());
        request.setStorageKey(attachment.getStorageKey());
        request.setFileUrl(attachment.getUrl());
        request.setFileFormat(resolveFormat(attachment.getFileName()));
        return ApiResult.ok(mediaTranscriptionApplicationService.createFollowupTask(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getDataScope(),
                request));
    }

    @PostMapping("/list")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:followup:view')")
    public ApiResult<List<MediaTranscriptionResponse>> list(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(mediaTranscriptionApplicationService.listByFollowup(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getDataScope(),
                request.getId()));
    }

    private String resolveFormat(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }
}
