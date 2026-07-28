package com.hz.crm.web.knowledge;

import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.knowledge.dto.KnowledgeDocumentQuery;
import com.hz.crm.knowledge.dto.KnowledgeDocumentRequest;
import com.hz.crm.knowledge.dto.KnowledgeDocumentResponse;
import com.hz.crm.knowledge.dto.KnowledgeIngestRequest;
import com.hz.crm.knowledge.dto.KnowledgeIngestResponse;
import com.hz.crm.knowledge.dto.KnowledgeIngestTaskResponse;
import com.hz.crm.knowledge.dto.KnowledgeSearchRequest;
import com.hz.crm.knowledge.dto.KnowledgeSearchResponse;
import com.hz.crm.knowledge.service.KnowledgeDocumentService;
import com.hz.crm.web.support.IdRequest;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
@RequestMapping("/api/knowledge/document")
public class KnowledgeDocumentController {

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<PageData<KnowledgeDocumentResponse>> page(
            @RequestBody(required = false) KnowledgeDocumentQuery query, JwtPrincipal principal) {
        return ApiResult.ok(knowledgeDocumentService.page(principal.getTenantId(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<KnowledgeDocumentResponse> detail(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(knowledgeDocumentService.detail(principal.getTenantId(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<KnowledgeDocumentResponse> save(
            @Valid @RequestBody KnowledgeDocumentRequest request, JwtPrincipal principal) {
        return ApiResult.ok(knowledgeDocumentService.save(principal.getTenantId(), request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<KnowledgeIngestResponse> importFile(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam("file") MultipartFile file,
            JwtPrincipal principal) {
        validateFile(file);
        KnowledgeDocumentRequest request = new KnowledgeDocumentRequest();
        request.setTitle(resolveTitle(title, file));
        request.setSourceType(StringUtils.hasText(sourceType) ? sourceType : "DOCUMENT");
        request.setCategory(category);
        request.setTags(tags);
        request.setSourceUrl(sourceUrl);
        request.setObjectKey(resolveFileName(file));
        request.setContent(extractDocumentText(file));
        KnowledgeDocumentResponse response = knowledgeDocumentService.save(principal.getTenantId(), request);
        return ApiResult.ok(knowledgeDocumentService.ingest(principal.getTenantId(), Long.valueOf(response.getId())));
    }

    @PostMapping("/ingest")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<KnowledgeIngestResponse> ingest(
            @RequestBody KnowledgeIngestRequest request, JwtPrincipal principal) {
        return ApiResult.ok(knowledgeDocumentService.ingest(principal.getTenantId(), request));
    }

    @PostMapping("/ingest/task")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<KnowledgeIngestTaskResponse> ingestTask(
            @RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(knowledgeDocumentService.ingestTask(principal.getTenantId(), request.getId()));
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<KnowledgeSearchResponse> search(
            @RequestBody KnowledgeSearchRequest request, JwtPrincipal principal) {
        return ApiResult.ok(knowledgeDocumentService.search(principal.getTenantId(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:knowledge:manage')")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        knowledgeDocumentService.delete(principal.getTenantId(), request.getId());
        return ApiResult.ok(null);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("KB_FILE_001", "请上传知识文档");
        }
        if (isDocumentFile(file.getContentType(), file.getOriginalFilename())) {
            return;
        }
        throw new BusinessException("KB_FILE_002", "仅支持html、txt、md、docx文件");
    }

    private String resolveTitle(String title, MultipartFile file) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        return resolveFileName(file);
    }

    private String resolveFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            return "未命名知识文档";
        }
        return StringUtils.cleanPath(fileName);
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
            throw new BusinessException("KB_FILE_003", "知识文档读取失败");
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
