package com.hz.crm.web.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "crm.nacos.enabled", havingValue = "true")
public class NacosConfiguration {

    @Value("${crm.nacos.server-addr}")
    private String serverAddr;

    @Value("${crm.nacos.namespace:}")
    private String namespace;

    @Value("${crm.nacos.username:}")
    private String username;

    @Value("${crm.nacos.password:}")
    private String password;

    @Bean
    public ConfigService nacosConfigService() throws Exception {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        if (namespace != null && namespace.trim().length() > 0) {
            properties.put("namespace", namespace);
        }
        if (username != null && username.trim().length() > 0) {
            properties.put("username", username.trim());
        }
        if (password != null && password.trim().length() > 0) {
            properties.put("password", password.trim());
        }
        return NacosFactory.createConfigService(properties);
    }
}
