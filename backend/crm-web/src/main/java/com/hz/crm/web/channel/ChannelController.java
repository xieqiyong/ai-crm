package com.hz.crm.web.channel;

import com.hz.crm.application.channel.ChannelApplicationService;
import com.hz.crm.application.channel.dto.ChannelMediaImportRequest;
import com.hz.crm.application.channel.dto.ChannelPromoteRequest;
import com.hz.crm.application.channel.dto.ChannelQuery;
import com.hz.crm.application.channel.dto.ChannelResponse;
import com.hz.crm.application.channel.dto.ChannelSaveRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.channel.ChannelType;
import com.hz.crm.web.support.IdRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/channel")
public class ChannelController {

    @Autowired
    private ChannelApplicationService channelApplicationService;

    @Value("${crm.channel.upload-dir:./uploads/channel}")
    private String uploadDir;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:view')")
    public ApiResult<PageData<ChannelResponse>> page(
            @RequestBody(required = false) ChannelQuery query, JwtPrincipal principal) {
        return ApiResult.ok(channelApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:view')")
    public ApiResult<ChannelResponse> detail(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(channelApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:manage')")
    public ApiResult<ChannelResponse> save(@Valid @RequestBody ChannelSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(channelApplicationService.save(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping(value = "/media/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:media') or hasAuthority('crm:channel:manage')")
    public ApiResult<ChannelResponse> importMedia(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) ChannelType channelType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String remark,
            @RequestParam("file") MultipartFile file,
            JwtPrincipal principal) {
        validateMediaFile(file);
        String storageKey = saveMediaFile(principal.getTenantId(), file);
        ChannelMediaImportRequest request = new ChannelMediaImportRequest();
        request.setTitle(title);
        request.setChannelType(channelType);
        request.setSource(source);
        request.setContactName(contactName);
        request.setCompanyName(companyName);
        request.setPhone(phone);
        request.setEmail(email);
        request.setMediaFileName(resolveFileName(file));
        request.setMediaContentType(file.getContentType());
        request.setMediaSize(file.getSize());
        request.setMediaStorageKey(storageKey);
        request.setRemark(remark);
        return ApiResult.ok(channelApplicationService.importMedia(
                principal.getTenantId(), principal.getUserId(), request));
    }

    @PostMapping("/transcription/prepare")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:media') or hasAuthority('crm:channel:manage')")
    public ApiResult<ChannelResponse> prepareTranscription(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(channelApplicationService.prepareTranscription(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/analysis/prepare")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:media') or hasAuthority('crm:channel:manage')")
    public ApiResult<ChannelResponse> prepareAiAnalysis(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(channelApplicationService.prepareAiAnalysis(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/promote")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:promote')")
    public ApiResult<ChannelResponse> promote(@RequestBody ChannelPromoteRequest request, JwtPrincipal principal) {
        return ApiResult.ok(channelApplicationService.promoteToLead(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:manage')")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        channelApplicationService.delete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId());
        return ApiResult.ok(null);
    }

    private void validateMediaFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CHANNEL_002", "请上传录音或视频文件");
        }
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        if (isAudioOrVideo(contentType, fileName)) {
            return;
        }
        throw new BusinessException("CHANNEL_006", "仅支持录音或视频文件");
    }

    private String resolveFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            return "未命名音视频文件";
        }
        return StringUtils.cleanPath(fileName);
    }

    private String saveMediaFile(Long tenantId, MultipartFile file) {
        String fileName = resolveFileName(file);
        String storageKey = tenantId + "/" + System.currentTimeMillis() + "-" + fileName;
        Path rootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetPath = rootPath.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new BusinessException("CHANNEL_008", "文件路径不合法");
        }
        try {
            Files.createDirectories(targetPath.getParent());
            InputStream inputStream = file.getInputStream();
            try {
                Files.copy(inputStream, targetPath);
            } finally {
                inputStream.close();
            }
            return storageKey;
        } catch (IOException ex) {
            throw new BusinessException("CHANNEL_009", "音视频文件保存失败");
        }
    }

    private boolean isAudioOrVideo(String contentType, String fileName) {
        if (StringUtils.hasText(contentType)) {
            String lowerContentType = contentType.toLowerCase();
            if (lowerContentType.startsWith("audio/") || lowerContentType.startsWith("video/")) {
                return true;
            }
        }
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".mp3")
                || lowerName.endsWith(".wav")
                || lowerName.endsWith(".m4a")
                || lowerName.endsWith(".aac")
                || lowerName.endsWith(".flac")
                || lowerName.endsWith(".mp4")
                || lowerName.endsWith(".mov")
                || lowerName.endsWith(".avi")
                || lowerName.endsWith(".mkv")
                || lowerName.endsWith(".webm");
    }
}
