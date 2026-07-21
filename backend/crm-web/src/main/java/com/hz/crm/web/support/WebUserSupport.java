package com.hz.crm.web.support;

import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class WebUserSupport {

    public JwtPrincipal current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal)) {
            throw new BusinessException("AUTH_003", "用户未登录");
        }
        return (JwtPrincipal) authentication.getPrincipal();
    }
}
