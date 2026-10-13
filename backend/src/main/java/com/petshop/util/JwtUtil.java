package com.petshop.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类（增强版，支持 Token 刷新）
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /**
     * 曾经写在仓库里的示例密钥前缀。
     * 它是公开的，拿它签出的 token 任何人都能伪造，因此命中时一律视为未配置。
     */
    private static final String LEAKED_DEFAULT_SECRET_PREFIX = "petshop_secret_key_2024";

    /** HS256 至少需要 32 字节（256 位）的密钥 */
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    /**
     * 未配置密钥时生成本次运行专用的随机密钥：
     * 仓库里不再存在可用的默认密钥，线上必须通过 JWT_SECRET 显式注入。
     */
    @PostConstruct
    void initSigningSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            secret = randomSecret();
            log.warn("未配置 JWT_SECRET，已生成本次运行专用的随机密钥；"
                    + "重启后已签发的 token 会全部失效，生产环境请显式配置 JWT_SECRET");
            return;
        }
        secret = secret.trim();
        if (secret.startsWith(LEAKED_DEFAULT_SECRET_PREFIX)) {
            secret = randomSecret();
            log.warn("检测到仓库中的示例 JWT 密钥，已替换为随机密钥；请在环境变量中配置独立的 JWT_SECRET");
            return;
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET 长度不足 " + MIN_SECRET_LENGTH + " 位，存在被暴力破解的风险，请配置更长的随机密钥");
        }
    }

    private String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    
    // Token 刷新时间窗口（过期前 30 分钟可刷新）
    private static final long REFRESH_WINDOW = 30 * 60 * 1000;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Token
     */
    public String generateToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("JWT 解析失败", e);
            return null;
        }
    }

    /**
     * 从 Token 获取用户 ID
     */
    public Integer getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object sub = claims.get("sub");
            log.info("从 Token 获取 sub 字段：{}, 类型：{}", sub, sub != null ? sub.getClass().getName() : "null");
            if (sub instanceof Integer) {
                return (Integer) sub;
            } else if (sub instanceof String) {
                try {
                    return Integer.parseInt((String) sub);
                } catch (NumberFormatException e) {
                    log.error("无法将 sub 转换为 Integer: {}", sub);
                    return null;
                }
            } else if (sub instanceof Long) {
                return ((Long) sub).intValue();
            }
        }
        return null;
    }

    /**
     * 从 Token 获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }

    /**
     * 检查 Token 是否过期
     */
    public boolean isTokenExpired(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return true;
        }
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("JWT 验证失败", e);
            return false;
        }
    }
    
    /**
     * 检查 Token 是否需要刷新（在刷新时间窗口内）
     */
    public boolean needsRefresh(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        
        Date expiration = claims.getExpiration();
        Date now = new Date();
        long timeUntilExpiry = expiration.getTime() - now.getTime();
        
        // 如果距离过期时间小于刷新窗口，则需要刷新
        return timeUntilExpiry < REFRESH_WINDOW && timeUntilExpiry > 0;
    }
    
    /**
     * 刷新 Token（延长有效期）
     */
    public String refreshToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        
        // 检查是否可以刷新（未过期且在刷新窗口内）
        if (isTokenExpired(token)) {
            log.warn("Token 已过期，无法刷新");
            return null;
        }
        
        // 提取原有声明
        Map<String, Object> newClaims = Map.of(
            "sub", claims.get("sub"),
            "username", claims.get("username", String.class)
        );
        
        // 生成新 Token
        return generateToken(newClaims);
    }
}
