package com.hz.crm.common.nacos;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@ConditionalOnBean(NamingService.class)
public class NacosServiceRegistrar implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    @Autowired
    private NamingService namingService;

    @Autowired
    private NacosDiscoveryProperties properties;

    @Autowired
    private Environment environment;

    private volatile boolean registered = false;

    private String registeredServiceName;

    private String registeredGroupName;

    private String registeredIp;

    private int registeredPort;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        register();
    }

    @Override
    public void destroy() {
        unregister();
    }

    private void register() {
        try {
            String serviceName = resolveServiceName();
            String groupName = environment.getProperty("crm.nacos.group", "DEFAULT_GROUP");
            String ip = resolveIp();
            int port = resolvePort();
            Instance instance = new Instance();
            instance.setIp(ip);
            instance.setPort(port);
            instance.setEphemeral(properties.isEphemeral());
            instance.setWeight(properties.getWeight());
            instance.setClusterName(properties.getClusterName());
            instance.setMetadata(resolveMetadata());
            namingService.registerInstance(serviceName, groupName, instance);
            registered = true;
            registeredServiceName = serviceName;
            registeredGroupName = groupName;
            registeredIp = ip;
            registeredPort = port;
            log.info("Nacos服务注册完成，服务名：{}，分组：{}，地址：{}:{}", serviceName, groupName, ip, port);
        } catch (Exception ex) {
            throw new IllegalStateException("Nacos服务注册失败：" + ex.getMessage(), ex);
        }
    }

    private void unregister() {
        if (!registered) {
            return;
        }
        try {
            namingService.deregisterInstance(registeredServiceName, registeredGroupName, registeredIp, registeredPort);
            log.info("Nacos服务注销完成，服务名：{}，地址：{}:{}", registeredServiceName, registeredIp, registeredPort);
        } catch (Exception ex) {
            log.warn("Nacos服务注销失败，服务名：{}，原因：{}", registeredServiceName, ex.getMessage());
        }
    }

    private String resolveServiceName() {
        if (StringUtils.hasText(properties.getServiceName())) {
            return properties.getServiceName().trim();
        }
        return environment.getProperty("spring.application.name", "crm-service");
    }

    private int resolvePort() {
        if (properties.getPort() > 0) {
            return properties.getPort();
        }
        Integer localPort = environment.getProperty("local.server.port", Integer.class);
        if (localPort != null && localPort.intValue() > 0) {
            return localPort.intValue();
        }
        return environment.getProperty("server.port", Integer.class, Integer.valueOf(8080)).intValue();
    }

    private String resolveIp() throws Exception {
        if (StringUtils.hasText(properties.getIp())) {
            return properties.getIp().trim();
        }
        String hostAddress = findSiteLocalAddress();
        if (StringUtils.hasText(hostAddress)) {
            return hostAddress;
        }
        return InetAddress.getLocalHost().getHostAddress();
    }

    private String findSiteLocalAddress() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (!address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                    return address.getHostAddress();
                }
            }
        }
        return "";
    }

    private Map<String, String> resolveMetadata() {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("scheme", "http");
        metadata.put("app", environment.getProperty("spring.application.name", resolveServiceName()));
        metadata.putAll(properties.getMetadata());
        return metadata;
    }
}
