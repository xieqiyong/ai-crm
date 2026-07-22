package com.hz.crm.web;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@MapperScan(basePackages = "com.hz.crm", markerInterface = BaseMapper.class)
@EntityScan("com.hz.crm")
@SpringBootApplication(scanBasePackages = "com.hz.crm")
@EnableJpaRepositories(basePackages = "com.hz.crm")
public class CrmWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmWebApplication.class, args);
    }
}
