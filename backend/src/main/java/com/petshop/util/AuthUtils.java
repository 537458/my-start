package com.petshop.util;

import org.springframework.security.core.Authentication;

/**
 * 从 Spring Security 上下文中取出当前登录用户ID。
 * JwtAuthenticationFilter 可能写入 Integer 或 String 类型的主体，这里统一收敛。
 */
public final class AuthUtils {

    private AuthUtils() {
    }

    public static Integer resolveUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Integer) {
            return (Integer) principal;
        }
        if (principal instanceof String) {
            try {
                return Integer.parseInt((String) principal);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
