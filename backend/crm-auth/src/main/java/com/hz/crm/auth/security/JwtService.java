package com.hz.crm.auth.security;

import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.dto.PermissionProfile;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    @Autowired
    private JwtProperties jwtProperties;

    public JwtPrincipal createPrincipal(SysUserEntity user, PermissionProfile profile, String sessionId) {
        JwtPrincipal principal = new JwtPrincipal();
        principal.setUserId(user.getId());
        principal.setTenantId(user.getTenantId());
        principal.setUsername(user.getUsername());
        principal.setDisplayName(user.getDisplayName());
        principal.setSessionId(sessionId);
        principal.setPermissions(new ArrayList<String>(profile.getPermissions()));
        principal.setMenuPermissions(new ArrayList<String>(profile.getMenuPermissions()));
        principal.setDataPermissions(new ArrayList<String>(profile.getDataPermissions()));
        principal.setDataScope(profile.getDataScope().name());
        return principal;
    }

    public String createToken(JwtPrincipal principal) {
        long now = System.currentTimeMillis();
        long expiresAt = now + jwtProperties.getTtlSeconds() * 1000L;
        principal.setExpiresAt(expiresAt);
        principal.setTtlSeconds(jwtProperties.getTtlSeconds());
        return Jwts.builder()
                .subject(String.valueOf(principal.getUserId()))
                .claim("tenantId", principal.getTenantId())
                .claim("username", principal.getUsername())
                .claim("displayName", principal.getDisplayName())
                .claim("sessionId", principal.getSessionId())
                .claim("permissions", new ArrayList<String>(principal.getPermissions()))
                .claim("menuPermissions", new ArrayList<String>(principal.getMenuPermissions()))
                .claim("dataPermissions", new ArrayList<String>(principal.getDataPermissions()))
                .claim("dataScope", principal.getDataScope())
                .issuedAt(new Date(now))
                .expiration(new Date(expiresAt))
                .signWith(signingKey())
                .compact();
    }

    public JwtPrincipal parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
        JwtPrincipal principal = new JwtPrincipal();
        principal.setUserId(Long.valueOf(claims.getSubject()));
        principal.setTenantId(readLong(claims.get("tenantId")));
        principal.setUsername(claims.get("username", String.class));
        principal.setDisplayName(claims.get("displayName", String.class));
        principal.setSessionId(claims.get("sessionId", String.class));
        if (claims.getExpiration() != null) {
            principal.setExpiresAt(claims.getExpiration().getTime());
            long ttlSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000L;
            principal.setTtlSeconds(ttlSeconds < 0L ? 0L : ttlSeconds);
        }
        principal.setPermissions(readStringList(claims.get("permissions")));
        principal.setMenuPermissions(readStringList(claims.get("menuPermissions")));
        principal.setDataPermissions(readStringList(claims.get("dataPermissions")));
        principal.setDataScope(claims.get("dataScope", String.class));
        return principal;
    }

    private Long readLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private List<String> readStringList(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> permissions = new ArrayList<String>();
            for (Object item : list) {
                if (item != null) {
                    permissions.add(String.valueOf(item));
                }
            }
            return permissions;
        }
        return new ArrayList<String>();
    }

    private SecretKey signingKey() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT密钥长度不能少于32字节");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
