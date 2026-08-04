package com.hz.crm.mcp.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CrmMcpDataSourceConfiguration {

    @Autowired
    private DataSource dataSource;

    @Bean
    @Primary
    public PlatformTransactionManager mcpTransactionManager() {
        return new DataSourceTransactionManager(dataSource);
    }
}
