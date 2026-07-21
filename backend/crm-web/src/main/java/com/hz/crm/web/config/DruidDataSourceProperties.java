package com.hz.crm.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "crm.datasource.druid")
public class DruidDataSourceProperties {

    private int initialSize = 2;

    private int minIdle = 2;

    private int maxActive = 20;

    private long maxWait = 60000L;

    private long timeBetweenEvictionRunsMillis = 60000L;

    private long minEvictableIdleTimeMillis = 300000L;

    private String validationQuery = "select 1";

    private boolean testWhileIdle = true;

    private boolean testOnBorrow;

    private boolean testOnReturn;

    private boolean poolPreparedStatements = true;

    private int maxPoolPreparedStatementPerConnectionSize = 20;
}
