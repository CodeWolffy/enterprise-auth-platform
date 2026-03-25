package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.auth.AuthCookieConstants;
import com.enterprise.auth.platform.auth.dto.CookieSessionResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.config.AuthorizationServerProperties;
import com.enterprise.auth.platform.config.FrontendProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.security.UserAccountJwtConverter;
import com.enterprise.auth.platform.tenant.TenantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OAuthCookieSessionService {

    private final FrontendProperties frontendProperties;
    private final SecurityProperties securityProperties;
    private final TenantProperties tenantProperties;
    private final JwtDecoder authorizationServerJwtDecoder;
    private final UserAccountJwtConverter userAccountJwtConverter;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String tokenEndpoint;

    public OAuthCookieSessionService(
            FrontendProperties frontendProperties,
            SecurityProperties securityProperties,
            TenantProperties tenantProperties,
            AuthorizationServerProperties authorizationServerProperties,
            JwtDecoder authorizationServerJwtDecoder,
            UserAccountJwtConverter userAccountJwtConverter,
            ObjectMapper objectMapper
    ) {
        this.frontendProperties = frontendProperties;
        this.securityProperties = securityProperties;
        this.tenantProperties = tenantProperties;
        this.authorizationServerJwtDecoder = authorizationServerJwtDecoder;
        this.userAccountJwtConverter = userAccountJwtConverter;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
        this.tokenEndpoint = UriComponentsBuilder.fromUriString(authorizationServerProperties.issuer())
                .path("/oauth2/token")
                .build()
                .toUriString();
    }

    public CookieSessionResponse exchangeAuthorizationCode(
            String code,
            String codeVerifier,
            String redirectUri,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        validateRedirectUri(redirectUri);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", frontendProperties.publicClientId());
        maybeAddClientSecret(params);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        params.add("code_verifier", codeVerifier);
        TokenPayload tokenPayload = requestToken(params, request);
        writeTokenCookies(tokenPayload, request, response);
        return toSessionResponse(tokenPayload);
    }

    public CookieSessionResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, AuthCookieConstants.REFRESH_TOKEN_COOKIE);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException("INVALID_TOKEN", "刷新令牌缺失");
        }
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", frontendProperties.publicClientId());
        maybeAddClientSecret(params);
        params.add("refresh_token", refreshToken);
        TokenPayload tokenPayload = requestToken(params, request);
        writeTokenCookies(tokenPayload, request, response);
        return toSessionResponse(tokenPayload);
    }

    public void clearCookies(HttpServletRequest request, HttpServletResponse response) {
        writeCookie(response, request, AuthCookieConstants.ACCESS_TOKEN_COOKIE, "", 0);
        writeCookie(response, request, AuthCookieConstants.REFRESH_TOKEN_COOKIE, "", 0);
    }

    private TokenPayload requestToken(LinkedMultiValueMap<String, String> params, HttpServletRequest request) {
        try {
            String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
            String forwardedFor = request.getHeader("X-Forwarded-For");
            String body = restClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers -> {
                        if (StringUtils.hasText(userAgent)) {
                            headers.set(HttpHeaders.USER_AGENT, userAgent);
                        }
                        if (StringUtils.hasText(forwardedFor)) {
                            headers.set("X-Forwarded-For", forwardedFor);
                        } else if (StringUtils.hasText(request.getRemoteAddr())) {
                            headers.set("X-Forwarded-For", request.getRemoteAddr());
                        }
                    })
                    .body(params)
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(body)) {
                throw new BusinessException("TOKEN_EXCHANGE_FAILED", "令牌端点返回空响应");
            }
            JsonNode node = objectMapper.readTree(body);
            String accessToken = node.path("access_token").asText(null);
            String refreshToken = node.path("refresh_token").asText(null);
            long expiresIn = node.path("expires_in").asLong(0L);
            if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(refreshToken) || expiresIn <= 0) {
                throw new BusinessException("TOKEN_EXCHANGE_FAILED", "令牌端点响应无效");
            }
            Jwt jwt = authorizationServerJwtDecoder.decode(accessToken);
            var claims = userAccountJwtConverter.toClaims(jwt);
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            return new TokenPayload(accessToken, refreshToken, expiresIn, claims.tenantId(), claims.sessionId(), expiresAt);
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String detail = StringUtils.hasText(responseBody) ? responseBody : ex.getStatusText();
            throw new BusinessException("TOKEN_EXCHANGE_FAILED", "令牌交换失败: " + detail);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("TOKEN_EXCHANGE_FAILED", "令牌交换失败: " + ex.getClass().getSimpleName());
        }
    }

    private CookieSessionResponse toSessionResponse(TokenPayload payload) {
        return new CookieSessionResponse(payload.tenantId(), payload.sessionId(), TimeSupport.toEpochMilli(payload.expiresAt()));
    }

    private void writeTokenCookies(TokenPayload tokenPayload, HttpServletRequest request, HttpServletResponse response) {
        long accessTtl = Math.max(1L, tokenPayload.expiresInSeconds());
        long refreshTtl = Math.max(1L, securityProperties.refreshTokenTtl().toSeconds());
        writeCookie(response, request, AuthCookieConstants.ACCESS_TOKEN_COOKIE, tokenPayload.accessToken(), accessTtl);
        writeCookie(response, request, AuthCookieConstants.REFRESH_TOKEN_COOKIE, tokenPayload.refreshToken(), refreshTtl);
    }

    private void writeCookie(
            HttpServletResponse response,
            HttpServletRequest request,
            String name,
            String value,
            long maxAgeSeconds
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(securityProperties.cookieSecure() || request.isSecure())
                .sameSite(resolveSameSite())
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveSameSite() {
        String value = securityProperties.cookieSameSite();
        if (!StringUtils.hasText(value)) {
            return "Lax";
        }
        String normalized = value.trim();
        List<String> allowed = List.of("Strict", "Lax", "None");
        return allowed.contains(normalized) ? normalized : "Lax";
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void maybeAddClientSecret(LinkedMultiValueMap<String, String> params) {
        if (StringUtils.hasText(frontendProperties.publicClientSecret())) {
            params.add("client_secret", frontendProperties.publicClientSecret());
        }
    }

    private void validateRedirectUri(String redirectUri) {
        if (!StringUtils.hasText(redirectUri)) {
            throw new BusinessException("INVALID_REDIRECT_URI", "重定向 URI 不能为空");
        }
        if (!frontendProperties.resolvedRedirectUris().contains(redirectUri)) {
            throw new BusinessException("INVALID_REDIRECT_URI", "重定向 URI 不在允许列表中");
        }
    }

    private record TokenPayload(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            String tenantId,
            String sessionId,
            Instant expiresAt
    ) {
    }
}
