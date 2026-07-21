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

    public String createToken(SysUserEntity user, PermissionProfile profile) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("tenantId", user.getTenantId())
                .claim("username", user.getUsername())
                .claim("displayName", user.getDisplayName())
                .claim("permissions", new ArrayList<String>(profile.getPermissions()))
                .claim("menuPermissions", new ArrayList<String>(profile.getMenuPermissions()))
                .claim("dataPermissions", new ArrayList<String>(profile.getDataPermissions()))
                .claim("dataScope", profile.getDataScope().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.getTtlSeconds() * 1000L))
                .signWith(signingKey())
                .compact();
    }

    public JwtPrincipal parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
        JwtPrincipal principal = new JwtPrincipal();
        principal.setUserId(Long.valueOf(claims.getSubject()));
        principal.setTenantId(claims.get("tenantId", String.class));
        principal.setUsername(claims.get("username", String.class));
        principal.setDisplayName(claims.get("displayName", String.class));
        principal.setPermissions(readStringList(claims.get("permissions")));
        principal.setMenuPermissions(readStringList(claims.get("menuPermissions")));
        principal.setDataPermissions(readStringList(claims.get("dataPermissions")));
        principal.setDataScope(claims.get("dataScope", String.class));
        return principal;
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
