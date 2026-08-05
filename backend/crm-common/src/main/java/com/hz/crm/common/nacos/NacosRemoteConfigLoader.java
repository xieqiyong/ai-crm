package com.hz.crm.common.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;

public class NacosRemoteConfigLoader implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "crmNacosRemoteConfig";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        Boolean enabled = environment.getProperty("crm.nacos.enabled", Boolean.class, Boolean.FALSE);
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        boolean failFast = Boolean.TRUE.equals(
                environment.getProperty("crm.nacos.fail-fast", Boolean.class, Boolean.TRUE));
        try {
            Properties properties = loadRemoteProperties(environment);
            if (properties.isEmpty()) {
                handleFailure(failFast, "Nacos配置为空");
                return;
            }
            addPropertySource(environment, properties);
            System.out.println("Nacos配置加载完成，配置项数量：" + properties.size());
        } catch (RuntimeException ex) {
            if (failFast && ex instanceof IllegalStateException
                    && StringUtils.hasText(ex.getMessage())
                    && ex.getMessage().startsWith("Nacos配置加载失败：")) {
                throw ex;
            }
            handleFailure(failFast, ex.getMessage());
        } catch (Exception ex) {
            handleFailure(failFast, ex.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private Properties loadRemoteProperties(ConfigurableEnvironment environment) throws Exception {
        ConfigService configService = createConfigService(environment);
        String defaultGroup = environment.getProperty("crm.nacos.group", "DEFAULT_GROUP");
        long timeoutMs = environment.getProperty("crm.nacos.timeout-ms", Long.class, Long.valueOf(5000L)).longValue();
        Properties merged = new Properties();
        String sharedDataIds = environment.getProperty("crm.nacos.shared-data-ids", "");
        String[] sharedItems = sharedDataIds.split(",");
        for (String sharedItem : sharedItems) {
            NacosConfigKey key = parseConfigKey(sharedItem, defaultGroup);
            if (key == null) {
                continue;
            }
            Properties sharedProperties = loadOne(configService, key.getDataId(), key.getGroup(), timeoutMs);
            merged.putAll(sharedProperties);
        }
        String dataId = environment.getProperty("crm.nacos.data-id", "crm.yaml");
        if (StringUtils.hasText(dataId)) {
            Properties mainProperties = loadOne(configService, dataId.trim(), defaultGroup, timeoutMs);
            merged.putAll(mainProperties);
        }
        return merged;
    }

    private ConfigService createConfigService(ConfigurableEnvironment environment) throws Exception {
        String serverAddr = environment.getProperty("crm.nacos.server-addr", "");
        if (!StringUtils.hasText(serverAddr)) {
            throw new IllegalStateException("Nacos服务地址不能为空");
        }
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr.trim());
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
        return NacosFactory.createConfigService(properties);
    }

    private Properties loadOne(ConfigService configService, String dataId, String group, long timeoutMs) throws Exception {
        String content = configService.getConfig(dataId, group, timeoutMs);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Nacos配置不存在或为空，dataId=" + dataId + "，group=" + group);
        }
        Properties properties = parseConfig(dataId, content);
        System.out.println("Nacos配置读取完成，dataId=" + dataId + "，group=" + group + "，配置项数量=" + properties.size());
        return properties;
    }

    private Properties parseConfig(String dataId, String content) throws Exception {
        String lowerDataId = dataId == null ? "" : dataId.toLowerCase();
        if (lowerDataId.endsWith(".yml") || lowerDataId.endsWith(".yaml")) {
            return parseYaml(content);
        }
        Properties properties = new Properties();
        ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
        InputStream inputStream = resource.getInputStream();
        try {
            properties.load(inputStream);
        } finally {
            inputStream.close();
        }
        return properties;
    }

    private Properties parseYaml(String content) {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
        Properties properties = factoryBean.getObject();
        return properties == null ? new Properties() : properties;
    }

    private void addPropertySource(ConfigurableEnvironment environment, Properties properties) {
        MutablePropertySources sources = environment.getPropertySources();
        PropertiesPropertySource propertySource = new PropertiesPropertySource(PROPERTY_SOURCE_NAME, properties);
        if (sources.contains(PROPERTY_SOURCE_NAME)) {
            sources.replace(PROPERTY_SOURCE_NAME, propertySource);
            return;
        }
        if (sources.contains("commandLineArgs")) {
            sources.addAfter("commandLineArgs", propertySource);
            return;
        }
        sources.addFirst(propertySource);
    }

    private NacosConfigKey parseConfigKey(String value, String defaultGroup) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        int index = text.indexOf(":");
        if (index <= 0) {
            return new NacosConfigKey(text, defaultGroup);
        }
        String dataId = text.substring(0, index).trim();
        String group = text.substring(index + 1).trim();
        if (!StringUtils.hasText(dataId)) {
            return null;
        }
        return new NacosConfigKey(dataId, StringUtils.hasText(group) ? group : defaultGroup);
    }

    private void handleFailure(boolean failFast, String message) {
        String text = StringUtils.hasText(message) ? message : "未知错误";
        if (failFast) {
            throw new IllegalStateException("Nacos配置加载失败：" + text);
        }
        System.err.println("Nacos配置加载失败，已忽略：" + text);
    }

    private static class NacosConfigKey {

        private String dataId;

        private String group;

        NacosConfigKey(String dataId, String group) {
            this.dataId = dataId;
            this.group = group;
        }

        String getDataId() {
            return dataId;
        }

        String getGroup() {
            return group;
        }
    }
}
