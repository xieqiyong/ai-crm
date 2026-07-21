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

    @Bean
    public ConfigService nacosConfigService() throws Exception {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        if (namespace != null && namespace.trim().length() > 0) {
            properties.put("namespace", namespace);
        }
        return NacosFactory.createConfigService(properties);
    }
}
