package com.hz.crm.mcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class McpAccessTokenFilter extends OncePerRequestFilter {

    @Value("${crm.mcp.access-token:}")
    private String accessToken;

    @Value("${crm.mcp.endpoint:/mcp}")
    private String endpoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!shouldCheck(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String actualToken = resolveToken(request);
        if (!accessToken.equals(actualToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"401\",\"message\":\"MCP访问令牌不正确\",\"success\":false}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldCheck(HttpServletRequest request) {
        if (!StringUtils.hasText(accessToken)) {
            return false;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String target = StringUtils.hasText(endpoint) ? endpoint : "/mcp";
        return path != null && path.startsWith(target);
    }

    private String resolveToken(HttpServletRequest request) {
        String headerToken = request.getHeader("X-CRM-MCP-TOKEN");
        if (StringUtils.hasText(headerToken)) {
            return headerToken.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return "";
    }
}
