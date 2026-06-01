package com.enterprise.auth.platform.common.web;

import com.enterprise.auth.platform.infrastructure.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final List<CidrMatcher> trustedProxyMatchers;

    public ClientIpResolver(RateLimitProperties properties) {
        this.trustedProxyMatchers = properties.resolvedTrustedProxies().stream()
                .map(CidrMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = normalize(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedClientIp = firstForwardedIp(request.getHeader(X_FORWARDED_FOR));
        if (StringUtils.hasText(forwardedClientIp)) {
            return forwardedClientIp;
        }

        String realIp = normalize(request.getHeader(X_REAL_IP));
        return StringUtils.hasText(realIp) ? realIp : remoteAddr;
    }

    boolean isTrustedProxy(String remoteAddr) {
        if (!StringUtils.hasText(remoteAddr) || trustedProxyMatchers.isEmpty()) {
            return false;
        }
        return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddr));
    }

    private String firstForwardedIp(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }
        return normalize(forwardedFor.split(",")[0]);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : UNKNOWN;
    }

    private static final class CidrMatcher {
        private final String cidr;

        private CidrMatcher(String cidr) {
            this.cidr = cidr;
        }

        private boolean matches(String address) {
            if (!StringUtils.hasText(cidr) || !StringUtils.hasText(address)) {
                return false;
            }
            if (!cidr.contains("/")) {
                return cidr.equals(address);
            }
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                return false;
            }
            try {
                int prefix = Integer.parseInt(parts[1]);
                if (parts[0].contains(":") || address.contains(":")) {
                    return "::1".equals(parts[0]) && "::1".equals(address);
                }
                int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
                return (ipv4ToInt(parts[0]) & mask) == (ipv4ToInt(address) & mask);
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private int ipv4ToInt(String ip) {
            String[] octets = ip.split("\\.");
            if (octets.length != 4) {
                throw new IllegalArgumentException("Invalid IPv4 address");
            }
            int value = 0;
            for (String octet : octets) {
                value = (value << 8) | Integer.parseInt(octet);
            }
            return value;
        }
    }
}