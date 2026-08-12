package com.hz.crm.common.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(NacosDiscoveryProperties.class)
@ConditionalOnProperty(name = "crm.nacos.discovery.enabled", havingValue = "true")
public class NacosDiscoveryConfiguration {

    @Autowired
    private Environment environment;

    @Bean
    public NamingService nacosNamingService() throws Exception {
        Properties properties = new Properties();
        properties.put("serverAddr", environment.getProperty("crm.nacos.server-addr", "localhost:8848"));
        String namespace = environment.getProperty("crm.nacos.namespace", "");
        if (StringUtils.hasText(namespace)) {
            properties.put("namespace", namespace.trim());
        }
        String username = environment.getProperty("crm.nacos.username", "");
        if (StringUtils.hasText(username)) {
            properties.put("username", username.trim());
        }
        String password = environment.getProperty("crm.nacos.password", "");
        if (StringUtils.hasText(password)) {
            properties.put("password", password.trim());
        }
        return NacosFactory.createNamingService(properties);
    }
}
