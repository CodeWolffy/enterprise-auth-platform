package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class JwtService {

    private final SecurityProperties securityProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        Assert.isTrue(securityProperties.jwtSecret().length() >= 32, "JWT 密钥长度不能少于 32 位");
        SecretKeySpec key = new SecretKeySpec(
                securityProperties.jwtSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public String issueAccessToken(UserAccount user, String sessionId) {
        return encode(user, sessionId, "access", securityProperties.accessTokenTtl());
    }

    public String issueRefreshToken(UserAccount user, String sessionId) {
        return encode(user, sessionId, "refresh", securityProperties.refreshTokenTtl());
    }

    public TokenClaims decode(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        Number userId = jwt.getClaim("uid");
        Number sessionVersion = jwt.getClaim("ver");
        return new TokenClaims(
                jwt.getId(),
                jwt.getClaimAsString("sid"),
                userId.longValue(),
                jwt.getSubject(),
                jwt.getClaimAsString("tenant"),
                jwt.getClaimAsString("typ"),
                sessionVersion.intValue()
        );
    }

    private String encode(UserAccount user, String sessionId, String tokenType, Duration ttl) {
        Instant now = Instant.now();
        // 自定义 JWT 仅保留最小身份声明，状态控制继续交给会话存储。
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.username())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("uid", user.id())
                .claim("sid", sessionId)
                .claim("tenant", user.tenantId())
                .claim("typ", tokenType)
                .claim("ver", user.sessionVersion())
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}
