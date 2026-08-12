package com.hz.crm.common.nacos;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@ConditionalOnBean(NamingService.class)
public class NacosServiceDiscoveryClient {

    @Autowired
    private NamingService namingService;

    @Autowired
    private org.springframework.core.env.Environment environment;

    private AtomicInteger counter = new AtomicInteger(0);

    public URI resolve(String serviceName, String fallbackUrl) {
        String realServiceName = trim(serviceName);
        if (!StringUtils.hasText(realServiceName)) {
            return fallback(fallbackUrl);
        }
        try {
            String groupName = environment.getProperty("crm.nacos.group", "DEFAULT_GROUP");
            List<Instance> instances = namingService.selectInstances(realServiceName, groupName, true);
            if (instances == null || instances.isEmpty()) {
                log.warn("Nacos未发现健康实例，服务名：{}，使用兜底地址：{}", realServiceName, fallbackUrl);
                return fallback(fallbackUrl);
            }
            Instance instance = choose(instances);
            String scheme = instance.getMetadata() == null ? null : instance.getMetadata().get("scheme");
            if (!StringUtils.hasText(scheme)) {
                scheme = "http";
            }
            return URI.create(scheme + "://" + instance.getIp() + ":" + instance.getPort());
        } catch (Exception ex) {
            log.warn("Nacos服务发现失败，服务名：{}，使用兜底地址：{}，原因：{}", realServiceName, fallbackUrl, ex.getMessage());
            return fallback(fallbackUrl);
        }
    }

    private Instance choose(List<Instance> instances) {
        int index = Math.floorMod(counter.getAndIncrement(), instances.size());
        return instances.get(index % instances.size());
    }

    private URI fallback(String fallbackUrl) {
        String value = trim(fallbackUrl);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("没有可用的服务地址");
        }
        return URI.create(value);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
