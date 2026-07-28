package com.hz.crm.web.attachment;

import com.hz.crm.common.api.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/attachment")
public class AttachmentController {

    @Autowired
    private AttachmentStorageService attachmentStorageService;

    @PostMapping("/upload-image")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<AttachmentUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(attachmentStorageService.uploadImage(file));
    }
}
