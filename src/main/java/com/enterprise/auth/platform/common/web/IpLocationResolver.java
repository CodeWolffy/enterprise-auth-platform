package com.enterprise.auth.platform.common.web;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * IP 归属地解析，基于 ip2region xdb 离线数据库。
 * 使用官方推荐的 {@link Ip2Region} 查询服务，通过 BufferCache 将 xdb 数据加载到内存，
 * 支持从 classpath/JAR 包中读取，且查询接口线程安全。
 * 数据源：<a href="https://github.com/lionsoul2014/ip2region/releases">ip2region releases</a>
 * 将 ip2region_v4.xdb 放置到 src/main/resources/ip2region/ip2region.xdb
 */
@Component
public class IpLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(IpLocationResolver.class);
    private static final String XDB_PATH = "ip2region/ip2region.xdb";

    private volatile Ip2Region ip2Region;

    @PostConstruct
    void init() {
        try (InputStream is = new ClassPathResource(XDB_PATH).getInputStream()) {
            Config v4Config = Config.custom()
                    .setCachePolicy(Config.BufferCache)
                    .setXdbInputStream(is)
                    .asV4();
            this.ip2Region = Ip2Region.create(v4Config, null);
            log.info("ip2region service loaded from classpath:{}", XDB_PATH);
        } catch (Exception e) {
            log.warn("ip2region service init failed for classpath:{}, IP location disabled: {}", XDB_PATH, e.getMessage());
        }
    }

    @PreDestroy
    void destroy() {
        if (ip2Region != null) {
            try {
                ip2Region.close();
            } catch (Exception e) {
                log.debug("failed to close ip2region service: {}", e.getMessage());
            }
        }
    }

    /**
     * 解析 IP 归属地，返回 "国家 省份 城市" 格式。
     * 私有/回环/保留段 IP 返回 "内网IP"；无数据文件、解析失败返回 null。
     */
    public String resolve(String ip) {
        if (ip2Region == null || ip == null || ip.isEmpty()) {
            return null;
        }
        if (isPrivateOrLoopback(ip)) {
            return "内网IP";
        }
        try {
            String raw = ip2Region.search(ip);
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            return formatLocation(raw);
        } catch (Exception e) {
            log.debug("ip2region search failed for {}: {}", ip, e.getMessage());
            return null;
        }
    }

    private static String formatLocation(String raw) {
        // ip2region v3.13+ 格式: 国家|省份|城市|ISP|iso-code
        // v3.12 及更早: 国家|省份|城市|ISP
        // Reserved 段: Reserved|Reserved|Reserved|0|0
        if (raw.startsWith("Reserved") || raw.startsWith("0|0|0")) {
            return "内网IP";
        }
        String[] parts = raw.split("\\|", 4);
        if (parts.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            String part = parts[i];
            if (part.isEmpty() || "0".equals(part)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static boolean isPrivateOrLoopback(String ip) {
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return true;
        }
        if (ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("172.16.")
                || ip.startsWith("172.17.")
                || ip.startsWith("172.18.")
                || ip.startsWith("172.19.")
                || ip.startsWith("172.20.")
                || ip.startsWith("172.21.")
                || ip.startsWith("172.22.")
                || ip.startsWith("172.23.")
                || ip.startsWith("172.24.")
                || ip.startsWith("172.25.")
                || ip.startsWith("172.26.")
                || ip.startsWith("172.27.")
                || ip.startsWith("172.28.")
                || ip.startsWith("172.29.")
                || ip.startsWith("172.30.")
                || ip.startsWith("172.31.")) {
            return true;
        }
        return false;
    }
}
