package com.enterprise.auth.platform.auth.security;

import com.enterprise.auth.platform.config.FrontendProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TrustedRequestOriginValidator {

    private final FrontendProperties frontendProperties;

    public TrustedRequestOriginValidator(FrontendProperties frontendProperties) {
        this.frontendProperties = frontendProperties;
    }

    public void requireTrustedBrowserOrigin(HttpServletRequest request) {
        Set<String> trustedOrigins = trustedOrigins(request);
        String requestOrigin = resolveRequestOrigin(request);
        if (!StringUtils.hasText(requestOrigin) || !trustedOrigins.contains(requestOrigin)) {
            throw new AccessDeniedException("请求来源不可信");
        }
    }

    private Set<String> trustedOrigins(HttpServletRequest request) {
        Set<String> origins = new LinkedHashSet<>();
        frontendProperties.resolvedAllowedOrigins().forEach(origin -> {
            String normalized = normalizeOrigin(origin);
            if (StringUtils.hasText(normalized)) {
                origins.add(normalized);
            }
        });

        String serverOrigin = normalizeOrigin(request.getScheme() + "://" + request.getServerName()
                + resolvePortSuffix(request));
        if (StringUtils.hasText(serverOrigin)) {
            origins.add(serverOrigin);
        }
        return origins;
    }

    private String resolveRequestOrigin(HttpServletRequest request) {
        String originHeader = normalizeOrigin(request.getHeader("Origin"));
        if (StringUtils.hasText(originHeader)) {
            return originHeader;
        }
        String referer = request.getHeader("Referer");
        if (!StringUtils.hasText(referer)) {
            return null;
        }
        return normalizeOrigin(referer);
    }

    private String normalizeOrigin(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            int port = uri.getPort();
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            StringBuilder origin = new StringBuilder(scheme).append("://").append(host);
            if (port >= 0 && port != defaultPort(scheme)) {
                origin.append(":").append(port);
            }
            return origin.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolvePortSuffix(HttpServletRequest request) {
        int port = request.getServerPort();
        if (("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && port == 443)) {
            return "";
        }
        return ":" + port;
    }

    private int defaultPort(String scheme) {
        return switch (scheme) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }
}
