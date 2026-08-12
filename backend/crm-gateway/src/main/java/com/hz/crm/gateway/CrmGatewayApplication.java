package com.hz.crm.gateway;

import com.hz.crm.common.nacos.NacosRemoteConfigLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.hz.crm")
public class CrmGatewayApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CrmGatewayApplication.class);
        application.addListeners(new NacosRemoteConfigLoader());
        application.run(args);
    }
}
