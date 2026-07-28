package com.hz.crm.web.channel;

import com.hz.crm.application.channel.dto.ChannelDocumentImportRequest;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
        String storageKey = saveChannelFile(principal.getTenantId(), file);
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

    @PostMapping(value = "/document/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:media') or hasAuthority('crm:channel:manage')")
    public ApiResult<ChannelResponse> importDocument(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String remark,
            @RequestParam("file") MultipartFile file,
            JwtPrincipal principal) {
        validateDocumentFile(file);
        String storageKey = saveChannelFile(principal.getTenantId(), file);
        ChannelDocumentImportRequest request = new ChannelDocumentImportRequest();
        request.setTitle(title);
        request.setSource(source);
        request.setContactName(contactName);
        request.setCompanyName(companyName);
        request.setPhone(phone);
        request.setEmail(email);
        request.setMediaFileName(resolveFileName(file));
        request.setMediaContentType(file.getContentType());
        request.setMediaSize(file.getSize());
        request.setMediaStorageKey(storageKey);
        request.setDocumentText(extractDocumentText(file));
        request.setRemark(remark);
        return ApiResult.ok(channelApplicationService.importDocument(
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

    private void validateDocumentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CHANNEL_010", "请上传文档或HTML页面");
        }
        if (isDocumentFile(file.getContentType(), file.getOriginalFilename())) {
            return;
        }
        throw new BusinessException("CHANNEL_012", "仅支持html、txt、md、docx文件");
    }

    private String resolveFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            return "未命名渠道文件";
        }
        return StringUtils.cleanPath(fileName);
    }

    private String saveChannelFile(Long tenantId, MultipartFile file) {
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
            throw new BusinessException("CHANNEL_009", "渠道文件保存失败");
        }
    }

    private String extractDocumentText(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String fileName = resolveFileName(file).toLowerCase();
            if (fileName.endsWith(".docx")) {
                return normalizePlainText(extractDocxText(bytes));
            }
            String text = decodeText(bytes);
            if (fileName.endsWith(".html") || fileName.endsWith(".htm")
                    || isHtmlContentType(file.getContentType())) {
                return extractHtmlText(text);
            }
            return normalizePlainText(text);
        } catch (IOException ex) {
            throw new BusinessException("CHANNEL_013", "文档内容读取失败");
        }
    }

    private String decodeText(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) {
            return new String(bytes, Charset.forName("GB18030"));
        }
        return text;
    }

    private String extractHtmlText(String html) {
        String text = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|tr|section|article)>", "\n")
                .replaceAll("<[^>]+>", " ");
        return normalizePlainText(decodeHtml(text));
    }

    private String extractDocxText(byte[] bytes) throws IOException {
        StringBuilder builder = new StringBuilder();
        ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes));
        try {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if ("word/document.xml".equals(name)
                        || name.startsWith("word/header")
                        || name.startsWith("word/footer")) {
                    builder.append(extractXmlText(readZipEntry(zipInputStream))).append("\n");
                }
            }
        } finally {
            zipInputStream.close();
        }
        return builder.toString();
    }

    private String readZipEntry(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = zipInputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, length);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private String extractXmlText(String xml) {
        String text = xml
                .replaceAll("(?i)</w:p>", "\n")
                .replaceAll("(?i)<w:tab\\s*/>", " ")
                .replaceAll("<[^>]+>", " ");
        return decodeHtml(text);
    }

    private String decodeHtml(String value) {
        String text = value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        Pattern pattern = Pattern.compile("&#(\\d+);");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = Character.toString((char) Integer.parseInt(matcher.group(1)));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String normalizePlainText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.replace('\u00A0', ' ');
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll("\\n\\s*\\n+", "\n");
        return text.trim();
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

    private boolean isDocumentFile(String contentType, String fileName) {
        if (isHtmlContentType(contentType) || isTextContentType(contentType) || isDocxContentType(contentType)) {
            return true;
        }
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".html")
                || lowerName.endsWith(".htm")
                || lowerName.endsWith(".txt")
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".markdown")
                || lowerName.endsWith(".docx");
    }

    private boolean isHtmlContentType(String contentType) {
        return StringUtils.hasText(contentType) && contentType.toLowerCase().contains("text/html");
    }

    private boolean isTextContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String lowerContentType = contentType.toLowerCase();
        return lowerContentType.startsWith("text/plain")
                || lowerContentType.contains("markdown");
    }

    private boolean isDocxContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String lowerContentType = contentType.toLowerCase();
        return lowerContentType.contains("wordprocessingml.document");
    }
}
