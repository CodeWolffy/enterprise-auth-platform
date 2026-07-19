package com.enterprise.auth.platform.common.web;

import com.enterprise.auth.platform.infrastructure.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
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

        if (!UNKNOWN.equals(remoteAddr) && !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedClientIp = resolveForwardedClientIp(request.getHeader(X_FORWARDED_FOR));
        if (StringUtils.hasText(forwardedClientIp)) {
            return forwardedClientIp;
        }

        String realIp = normalize(request.getHeader(X_REAL_IP));
        if (StringUtils.hasText(realIp) && !UNKNOWN.equals(realIp) && isIpAddress(realIp)) {
            return realIp;
        }

        return UNKNOWN.equals(remoteAddr) ? "127.0.0.1" : remoteAddr;
    }

    boolean isTrustedProxy(String remoteAddr) {
        if (!StringUtils.hasText(remoteAddr) || trustedProxyMatchers.isEmpty()) {
            return false;
        }
        return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddr));
    }

    private String resolveForwardedClientIp(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }
        List<String> hops = new ArrayList<>();
        Collections.addAll(hops, forwardedFor.split(","));
        for (int index = hops.size() - 1; index >= 0; index--) {
            String candidate = normalize(hops.get(index));
            if (UNKNOWN.equals(candidate) || !isIpAddress(candidate)) {
                continue;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isIpAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.contains(":")) {
            try {
                return java.net.InetAddress.getByName(value).getAddress().length == 16;
            } catch (java.net.UnknownHostException ex) {
                return false;
            }
        }
        String[] octets = value.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            try {
                int part = Integer.parseInt(octet);
                if (part < 0 || part > 255) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
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
                if (parts[0].contains(":")) {
                    if (prefix < 0 || prefix > 128) {
                        return false;
                    }
                    return ipv6Matches(parts[0], address, prefix);
                }
                if (address.contains(":") || prefix < 0 || prefix > 32) {
                    return false;
                }
                int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
                return (ipv4ToInt(parts[0]) & mask) == (ipv4ToInt(address) & mask);
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private boolean ipv6Matches(String cidrAddr, String testAddr, int prefix) {
            try {
                java.net.InetAddress cidrInet = java.net.InetAddress.getByName(cidrAddr);
                java.net.InetAddress testInet = java.net.InetAddress.getByName(testAddr);
                byte[] cidrBytes = cidrInet.getAddress();
                byte[] testBytes = testInet.getAddress();
                if (cidrBytes.length != 16 || testBytes.length != 16) {
                    return false;
                }
                int fullBytes = prefix / 8;
                int remainingBits = prefix % 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (cidrBytes[i] != testBytes[i]) {
                        return false;
                    }
                }
                if (remainingBits > 0) {
                    int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                    if ((cidrBytes[fullBytes] & mask) != (testBytes[fullBytes] & mask)) {
                        return false;
                    }
                }
                return true;
            } catch (java.net.UnknownHostException e) {
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
                int part = Integer.parseInt(octet);
                if (part < 0 || part > 255) {
                    throw new IllegalArgumentException("Invalid IPv4 address");
                }
                value = (value << 8) | part;
            }
            return value;
        }
    }
}
