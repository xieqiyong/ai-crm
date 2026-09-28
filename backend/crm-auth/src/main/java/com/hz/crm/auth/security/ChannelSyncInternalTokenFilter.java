package com.hz.crm.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ChannelSyncInternalTokenFilter extends OncePerRequestFilter {

    private static final String IMPORT_PATH = "/api/internal/channel/source/import-excel";

    @Value("${crm.channel.sync.internal-token:}")
    private String internalToken;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!shouldCheck(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String requestToken = request.getHeader("X-CRM-Channel-Sync-Token");
        if (!matches(internalToken, requestToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":\"401\",\"message\":\"渠道同步内部令牌不正确\",\"success\":false}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldCheck(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && IMPORT_PATH.equals(request.getRequestURI());
    }

    private boolean matches(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        byte[] expectedBytes = expected.trim().getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
