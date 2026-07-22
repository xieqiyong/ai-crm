package com.hz.crm.web.observability;

import com.hz.crm.auth.security.CurrentUserContext;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.observability.dto.RequestLogRecord;
import com.hz.crm.observability.service.RequestLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLogFilter.class);

    @Autowired
    private RequestLogService requestLogService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        long start = System.currentTimeMillis();
        Exception thrown = null;
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            thrown = ex;
            throw ex;
        } finally {
            recordRequest(request, response, traceId, start, thrown);
            MDC.remove("traceId");
        }
    }

    private void recordRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            String traceId,
            long start,
            Exception thrown) {
        try {
            RequestLogRecord record = new RequestLogRecord();
            fillUser(record);
            record.setTraceId(traceId);
            record.setRequestMethod(request.getMethod());
            record.setRequestUri(request.getRequestURI());
            record.setClientIp(resolveClientIp(request));
            record.setUserAgent(request.getHeader("User-Agent"));
            record.setStatusCode(response.getStatus());
            record.setCostMillis(System.currentTimeMillis() - start);
            Object errorCode = request.getAttribute("crm.error.code");
            Object errorMessage = request.getAttribute("crm.error.message");
            if (thrown != null) {
                record.setErrorCode("SYS_001");
                record.setErrorMessage(thrown.getMessage());
            } else {
                record.setErrorCode(errorCode == null ? null : String.valueOf(errorCode));
                record.setErrorMessage(errorMessage == null ? null : String.valueOf(errorMessage));
            }
            record.setSuccess(record.getErrorCode() == null && response.getStatus() < 400);
            requestLogService.record(record);
        } catch (RuntimeException ex) {
            LOGGER.warn("请求日志写入失败", ex);
        }
    }

    private void fillUser(RequestLogRecord record) {
        JwtPrincipal principal = CurrentUserContext.getPrincipal();
        if (principal != null) {
            record.setTenantId(principal.getTenantId());
            record.setOperatorId(principal.getUserId());
            record.setUsername(principal.getUsername());
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.trim().length() == 0) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        if (value != null && value.trim().length() > 0) {
            int index = value.indexOf(',');
            if (index > 0) {
                return value.substring(0, index).trim();
            }
            return value.trim();
        }
        value = request.getHeader("X-Real-IP");
        if (value != null && value.trim().length() > 0) {
            return value.trim();
        }
        return request.getRemoteAddr();
    }
}
