package com.hz.crm.mcp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
        "com.hz.crm.application.followup"
})
public class CrmMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmMcpApplication.class, args);
    }
}
