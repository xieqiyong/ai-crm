package com.hz.crm.common.nacos;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "crm.nacos.discovery")
public class NacosDiscoveryProperties {

    private boolean enabled = false;

    private String serviceName;

    private String ip;

    private int port = 0;

    private String clusterName = "DEFAULT";

    private boolean ephemeral = true;

    private double weight = 1.0D;

    private Map<String, String> metadata = new LinkedHashMap<String, String>();
}
