package com.hz.crm.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.hz.crm")
@SpringBootApplication(scanBasePackages = "com.hz.crm")
public class CrmWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmWebApplication.class, args);
    }
}
