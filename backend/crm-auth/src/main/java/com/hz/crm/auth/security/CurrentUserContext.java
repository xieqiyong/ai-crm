package com.hz.crm.auth.security;

import com.hz.crm.common.exception.BusinessException;

public final class CurrentUserContext {

    private static final ThreadLocal<JwtPrincipal> PRINCIPAL_HOLDER = new ThreadLocal<JwtPrincipal>();

    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<String>();

    private CurrentUserContext() {
    }

    public static void setPrincipal(JwtPrincipal principal) {
        PRINCIPAL_HOLDER.set(principal);
    }

    public static JwtPrincipal getPrincipal() {
        return PRINCIPAL_HOLDER.get();
    }

    public static JwtPrincipal current() {
        JwtPrincipal principal = PRINCIPAL_HOLDER.get();
        if (principal == null) {
            throw new BusinessException("AUTH_003", "用户未登录");
        }
        return principal;
    }

    public static void setToken(String token) {
        TOKEN_HOLDER.set(token);
    }

    public static String getToken() {
        return TOKEN_HOLDER.get();
    }

    public static void clear() {
        PRINCIPAL_HOLDER.remove();
        TOKEN_HOLDER.remove();
    }
}
