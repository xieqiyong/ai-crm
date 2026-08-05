package com.hz.crm.mcp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.common.nacos.NacosRemoteConfigLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@MapperScan(basePackages = {"com.hz.crm.domain", "com.hz.crm.mcp"}, markerInterface = BaseMapper.class)
@SpringBootApplication(scanBasePackages = {
        "com.hz.crm.mcp",
        "com.hz.crm.common.id",
        "com.hz.crm.application.customer",
        "com.hz.crm.application.opportunity",
        "com.hz.crm.application.followup",
        "com.hz.crm.application.task"
})
public class CrmMcpApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CrmMcpApplication.class);
        application.addListeners(new NacosRemoteConfigLoader());
        application.run(args);
    }
}
