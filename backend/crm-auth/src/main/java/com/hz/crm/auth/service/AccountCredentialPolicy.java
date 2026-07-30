package com.hz.crm.auth.service;

import com.hz.crm.common.exception.BusinessException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AccountCredentialPolicy {

    public static final String USERNAME_HINT = "用户名为4至32位，必须以字母开头，仅支持字母、数字、下划线、短横线和点";

    public static final String PASSWORD_HINT = "密码为8至64位，必须同时包含大写字母、小写字母、数字和特殊字符，不能包含空格";

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{3,31}$");

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");

    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]");

    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[^A-Za-z0-9]");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    public void validateUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new BusinessException("ACCOUNT_USERNAME_001", USERNAME_HINT);
        }
    }

    public void validatePassword(String password) {
        if (password == null
                || password.length() < 8
                || password.length() > 64
                || !UPPERCASE_PATTERN.matcher(password).find()
                || !LOWERCASE_PATTERN.matcher(password).find()
                || !NUMBER_PATTERN.matcher(password).find()
                || !SPECIAL_PATTERN.matcher(password).find()
                || WHITESPACE_PATTERN.matcher(password).find()) {
            throw new BusinessException("ACCOUNT_PASSWORD_001", PASSWORD_HINT);
        }
    }
}
