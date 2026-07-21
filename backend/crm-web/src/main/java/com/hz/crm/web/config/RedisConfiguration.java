package com.hz.crm.web.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
@ConditionalOnProperty(name = "crm.redis.enabled", havingValue = "true")
public class RedisConfiguration {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean
    public JedisPooled jedisPooled() {
        if (password == null || password.trim().length() == 0) {
            return new JedisPooled(host, port);
        }
        return new JedisPooled(URI.create("redis://:" + password + "@" + host + ":" + port));
    }
}
