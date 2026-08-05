package com.hz.crm.web.attachment;

import com.hz.crm.auth.security.CurrentUserContext;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.time.DateTimes;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentStorageService {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired(required = false)
    private MinioClient minioClient;

    private volatile boolean bucketReady;

    @Value("${crm.storage.minio.enabled:false}")
    private boolean minioEnabled;

    @Value("${crm.storage.minio.bucket:crm}")
    private String minioBucket;

    @Value("${crm.storage.minio.public-read:true}")
    private boolean minioPublicRead;

    @Value("${crm.storage.minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${crm.storage.minio.public-url:}")
    private String minioPublicUrl;

    @Value("${crm.storage.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${crm.storage.public-path:/uploads}")
    private String publicPath;

    @Value("${crm.media.transcription.max-file-size-bytes:536870912}")
    private long mediaMaxFileSize;

    public AttachmentUploadResponse uploadImage(MultipartFile file) {
        validateImage(file);
        String originalName = resolveOriginalName(file);
        String storageKey = buildStorageKey(CurrentUserContext.current().getTenantId(), originalName);
        if (minioEnabled && minioClient != null) {
            return uploadToMinio(file, originalName, storageKey);
        }
        return uploadToLocal(file, originalName, storageKey);
    }

    public AttachmentUploadResponse uploadFile(MultipartFile file) {
        validateFile(file);
        String originalName = resolveOriginalName(file);
        String storageKey = buildStorageKey(CurrentUserContext.current().getTenantId(), originalName);
        if (minioEnabled && minioClient != null) {
            return uploadToMinio(file, originalName, storageKey);
        }
        return uploadToLocal(file, originalName, storageKey);
    }

    public AttachmentUploadResponse uploadMedia(MultipartFile file) {
        validateMedia(file);
        String originalName = resolveOriginalName(file);
        String storageKey = buildStorageKey(CurrentUserContext.current().getTenantId(), originalName, "media/raw", ".dat");
        if (minioEnabled && minioClient != null) {
            return uploadToMinio(file, originalName, storageKey);
        }
        return uploadToLocal(file, originalName, storageKey);
    }

    public AttachmentUploadResponse uploadGeneratedFile(
            Long tenantId,
            Path filePath,
            String originalName,
            String contentType,
            String prefix) {
        if (tenantId == null) {
            throw new BusinessException("ATTACHMENT_011", "租户编号不能为空");
        }
        if (filePath == null || !Files.exists(filePath)) {
            throw new BusinessException("ATTACHMENT_012", "生成文件不存在");
        }
        String safeName = StringUtils.hasText(originalName) ? originalName : filePath.getFileName().toString();
        String storageKey = buildStorageKey(tenantId, safeName, StringUtils.hasText(prefix) ? prefix : "media/generated");
        if (minioEnabled && minioClient != null) {
            return uploadPathToMinio(filePath, safeName, contentType, storageKey);
        }
        return uploadPathToLocal(filePath, safeName, contentType, storageKey);
    }

    public InputStream openStoredObject(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new BusinessException("ATTACHMENT_013", "文件存储地址不能为空");
        }
        try {
            if (minioEnabled && minioClient != null) {
                return minioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(storageKey)
                        .build());
            }
            return Files.newInputStream(resolveLocalPath(storageKey));
        } catch (Exception ex) {
            throw new BusinessException("ATTACHMENT_014", "读取存储文件失败：" + ex.getMessage());
        }
    }

    private AttachmentUploadResponse uploadToMinio(MultipartFile file, String originalName, String storageKey) {
        try {
            ensureBucket();
            InputStream inputStream = file.getInputStream();
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(storageKey)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            } finally {
                inputStream.close();
            }
            return response(file, originalName, storageKey, buildMinioUrl(storageKey));
        } catch (Exception ex) {
            throw new BusinessException("ATTACHMENT_004", "文件上传到MinIO失败：" + ex.getMessage());
        }
    }

    private AttachmentUploadResponse uploadToLocal(MultipartFile file, String originalName, String storageKey) {
        try {
            Path targetPath = resolveLocalPath(storageKey);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            String url = normalizePublicPath(publicPath) + "/" + storageKey.replace("\\", "/");
            return response(file, originalName, storageKey, url);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("ATTACHMENT_006", "文件保存失败：" + ex.getMessage());
        }
    }

    private AttachmentUploadResponse uploadPathToMinio(
            Path filePath,
            String originalName,
            String contentType,
            String storageKey) {
        try {
            ensureBucket();
            long size = Files.size(filePath);
            InputStream inputStream = Files.newInputStream(filePath);
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(storageKey)
                        .stream(inputStream, size, -1)
                        .contentType(contentType)
                        .build());
            } finally {
                inputStream.close();
            }
            return response(originalName, contentType, size, storageKey, buildMinioUrl(storageKey));
        } catch (Exception ex) {
            throw new BusinessException("ATTACHMENT_015", "生成文件上传到MinIO失败：" + ex.getMessage());
        }
    }

    private AttachmentUploadResponse uploadPathToLocal(
            Path filePath,
            String originalName,
            String contentType,
            String storageKey) {
        try {
            Path targetPath = resolveLocalPath(storageKey);
            Files.createDirectories(targetPath.getParent());
            Files.copy(filePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String url = normalizePublicPath(publicPath) + "/" + storageKey.replace("\\", "/");
            return response(originalName, contentType, Files.size(targetPath), storageKey, url);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("ATTACHMENT_016", "生成文件保存失败：" + ex.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioBucket).build());
            }
            if (minioPublicRead) {
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(minioBucket)
                        .config(buildBucketPolicy())
                        .build());
            }
            bucketReady = true;
        }
    }

    private String buildBucketPolicy() {
        String bucket = minioBucket.replace("\"", "");
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
    }

    private AttachmentUploadResponse response(MultipartFile file, String originalName, String storageKey, String url) {
        return response(originalName, file.getContentType(), file.getSize(), storageKey, url);
    }

    private AttachmentUploadResponse response(
            String originalName,
            String contentType,
            Long size,
            String storageKey,
            String url) {
        AttachmentUploadResponse response = new AttachmentUploadResponse();
        response.setFileName(originalName);
        response.setContentType(contentType);
        response.setSize(size);
        response.setStorageKey(storageKey);
        response.setUrl(url);
        return response;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("ATTACHMENT_001", "请选择要上传的图片");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException("ATTACHMENT_002", "仅支持上传图片文件");
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            throw new BusinessException("ATTACHMENT_003", "图片不能超过10MB");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("ATTACHMENT_007", "请选择要上传的文件");
        }
        if (file.getSize() > 30 * 1024 * 1024L) {
            throw new BusinessException("ATTACHMENT_008", "文件不能超过30MB");
        }
    }

    private void validateMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("ATTACHMENT_009", "请选择要上传的音视频文件");
        }
        if (file.getSize() > mediaMaxFileSize) {
            throw new BusinessException("ATTACHMENT_010", "音视频文件不能超过" + readableSize(mediaMaxFileSize));
        }
        String originalName = resolveOriginalName(file);
        String contentType = file.getContentType();
        String extension = resolveExtension(originalName, ".dat");
        boolean mediaContentType = StringUtils.hasText(contentType)
                && (contentType.toLowerCase(Locale.ROOT).startsWith("audio/")
                || contentType.toLowerCase(Locale.ROOT).startsWith("video/"));
        boolean mediaExtension = ".mp3".equals(extension)
                || ".wav".equals(extension)
                || ".m4a".equals(extension)
                || ".aac".equals(extension)
                || ".ogg".equals(extension)
                || ".mp4".equals(extension)
                || ".mov".equals(extension)
                || ".mkv".equals(extension)
                || ".webm".equals(extension)
                || ".avi".equals(extension);
        if (!mediaContentType && !mediaExtension) {
            throw new BusinessException("ATTACHMENT_017", "仅支持上传音频或视频文件");
        }
    }

    private String resolveOriginalName(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            return "image";
        }
        return Paths.get(originalName).getFileName().toString();
    }

    private String buildStorageKey(Long tenantId, String originalName) {
        return buildStorageKey(tenantId, originalName, "richtext");
    }

    private String buildStorageKey(Long tenantId, String originalName, String prefix) {
        return buildStorageKey(tenantId, originalName, prefix, ".png");
    }

    private String buildStorageKey(Long tenantId, String originalName, String prefix, String defaultExtension) {
        String extension = resolveExtension(originalName, defaultExtension);
        String day = DateTimes.now().format(DAY_FORMATTER);
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        return trimEnd(prefix) + "/" + tenantId + "/" + day + "/" + fileName;
    }

    private String resolveExtension(String fileName) {
        return resolveExtension(fileName, ".png");
    }

    private String resolveExtension(String fileName, String defaultExtension) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return defaultExtension;
        }
        String extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (extension.length() > 10) {
            return defaultExtension;
        }
        return extension;
    }

    private Path resolveLocalPath(String storageKey) {
        Path rootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetPath = rootPath.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new BusinessException("ATTACHMENT_005", "文件路径不合法");
        }
        return targetPath;
    }

    private String readableSize(long size) {
        long mb = size / 1024 / 1024;
        if (mb > 0) {
            return mb + "MB";
        }
        long kb = size / 1024;
        if (kb > 0) {
            return kb + "KB";
        }
        return size + "B";
    }

    private String buildMinioUrl(String storageKey) {
        String base = StringUtils.hasText(minioPublicUrl) ? minioPublicUrl : minioEndpoint;
        return trimEnd(base) + "/" + minioBucket + "/" + storageKey;
    }

    private String normalizePublicPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "/uploads";
        }
        String path = value.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return trimEnd(path);
    }

    private String trimEnd(String value) {
        String text = value == null ? "" : value.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
