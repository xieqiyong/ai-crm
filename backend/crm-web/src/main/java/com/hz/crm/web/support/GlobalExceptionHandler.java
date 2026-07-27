package com.hz.crm.web.support;

import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        request.setAttribute("crm.error.code", exception.getCode());
        request.setAttribute("crm.error.message", exception.getMessage());
        return ApiResult.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getAllErrors().isEmpty()
                ? "请求参数不正确"
                : exception.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        request.setAttribute("crm.error.code", "PARAM_001");
        request.setAttribute("crm.error.message", message);
        return ApiResult.fail("PARAM_001", message);
    }

    @ExceptionHandler(BindException.class)
    public ApiResult<Void> handleBindException(BindException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getAllErrors().isEmpty()
                ? "请求参数不正确"
                : exception.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        request.setAttribute("crm.error.code", "PARAM_001");
        request.setAttribute("crm.error.message", message);
        return ApiResult.fail("PARAM_001", message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception exception, HttpServletRequest request) {
        request.setAttribute("crm.error.code", "SYS_001");
        request.setAttribute("crm.error.message", exception.getMessage());
        log.error("系统处理异常，路径：{}，方法：{}", request.getRequestURI(), request.getMethod(), exception);
        return ApiResult.fail("SYS_001", "系统处理异常");
    }
}
