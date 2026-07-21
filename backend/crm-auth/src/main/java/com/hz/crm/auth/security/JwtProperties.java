package com.hz.crm.auth.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "crm.jwt")
public class JwtProperties {

    private String secret;

    private long ttlSeconds = 86400L;
}
