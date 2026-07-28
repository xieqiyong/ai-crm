package com.hz.crm.web.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadResourceConfiguration implements WebMvcConfigurer {

    @Value("${crm.storage.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${crm.storage.public-path:/uploads}")
    private String publicPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path rootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler(normalizePublicPath(publicPath) + "/**")
                .addResourceLocations(rootPath.toUri().toString());
    }

    private String normalizePublicPath(String value) {
        String path = StringUtils.hasText(value) ? value.trim() : "/uploads";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
