package com.hz.crm.web.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "crm.storage.minio.enabled", havingValue = "true")
public class MinioConfiguration {

    @Value("${crm.storage.minio.endpoint}")
    private String endpoint;

    @Value("${crm.storage.minio.access-key}")
    private String accessKey;

    @Value("${crm.storage.minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }
}
