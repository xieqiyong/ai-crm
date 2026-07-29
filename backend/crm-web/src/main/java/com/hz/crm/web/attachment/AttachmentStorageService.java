package com.hz.crm.web.attachment;

import com.hz.crm.auth.security.CurrentUserContext;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.time.DateTimes;
import io.minio.BucketExistsArgs;
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
            Path rootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetPath = rootPath.resolve(storageKey).normalize();
            if (!targetPath.startsWith(rootPath)) {
                throw new BusinessException("ATTACHMENT_005", "文件路径不合法");
            }
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
        AttachmentUploadResponse response = new AttachmentUploadResponse();
        response.setFileName(originalName);
        response.setContentType(file.getContentType());
        response.setSize(file.getSize());
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

    private String resolveOriginalName(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            return "image";
        }
        return Paths.get(originalName).getFileName().toString();
    }

    private String buildStorageKey(Long tenantId, String originalName) {
        String extension = resolveExtension(originalName);
        String day = DateTimes.now().format(DAY_FORMATTER);
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        return "richtext/" + tenantId + "/" + day + "/" + fileName;
    }

    private String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return ".png";
        }
        String extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (extension.length() > 10) {
            return ".png";
        }
        return extension;
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
