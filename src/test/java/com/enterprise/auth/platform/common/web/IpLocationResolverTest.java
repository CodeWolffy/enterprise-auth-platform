package com.enterprise.auth.platform.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class IpLocationResolverTest {

    private final IpLocationResolver resolver = new IpLocationResolver();

    @Test
    void shouldReturnNullWhenInputIsNull() {
        resolver.init();
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void shouldReturnNullWhenInputIsEmpty() {
        resolver.init();
        assertThat(resolver.resolve("")).isNull();
    }

    @Test
    void shouldReturnInnerNetForLoopback() {
        resolver.init();
        assertThat(resolver.resolve("127.0.0.1")).isEqualTo("内网IP");
        assertThat(resolver.resolve("0:0:0:0:0:0:0:1")).isEqualTo("内网IP");
        assertThat(resolver.resolve("::1")).isEqualTo("内网IP");
    }

    @Test
    void shouldReturnInnerNetForPrivateRanges() {
        resolver.init();
        assertThat(resolver.resolve("10.0.0.1")).isEqualTo("内网IP");
        assertThat(resolver.resolve("192.168.1.100")).isEqualTo("内网IP");
        assertThat(resolver.resolve("172.16.0.1")).isEqualTo("内网IP");
        assertThat(resolver.resolve("172.31.255.255")).isEqualTo("内网IP");
    }

    @Test
    void shouldResolvePublicIpWhenXdbAvailable() {
        // 验证 xdb 可用时，公网 IP 能正常解析而非返回 null
        resolver.init();
        assertThat(resolver.resolve("114.114.114.114"))
                .as("114.114.114.114 应正常解析归属地，说明 xdb 数据可用")
                .isNotNull();
    }

    @Test
    void shouldDetectXdbAvailability() throws Exception {
        // 验证数据文件是否已放置到预期路径
        ClassPathResource resource = new ClassPathResource("ip2region/ip2region.xdb");
        assertThat(resource.exists())
                .as("ip2region.xdb 应放置在 src/main/resources/ip2region/ip2region.xdb，否则公网 IP 解析将不可用")
                .isTrue();
    }

    @Test
    void shouldResolveShanghaiForSamplePublicIp() {
        resolver.init();
        String location = resolver.resolve("139.196.7.151");
        assertThat(location)
                .as("139.196.7.151 应解析为上海归属地，若返回 null 或非上海，说明 xdb 数据不可用或数据不准")
                .isNotNull()
                .contains("上海");
    }

    @Test
    void shouldResolveMultipleCitiesCorrectly() {
        resolver.init();
        assertThat(resolver.resolve("112.22.0.205"))
                .as("112.22.0.205 应解析为苏州")
                .isNotNull()
                .contains("苏州");
        assertThat(resolver.resolve("139.196.221.11"))
                .as("139.196.221.11 应解析为上海")
                .isNotNull()
                .contains("上海");
        assertThat(resolver.resolve("120.231.24.114"))
                .as("120.231.24.114 应解析为湛江")
                .isNotNull()
                .contains("湛江");
        assertThat(resolver.resolve("112.42.95.159"))
                .as("112.42.95.159 应解析为大连")
                .isNotNull()
                .contains("大连");
    }

    @Test
    void shouldResolveWithinPerformanceBudget() {
        resolver.init();
        long start = System.nanoTime();
        String location = resolver.resolve("139.196.7.151");
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(location)
                .as("139.196.7.151 应解析为上海")
                .isNotNull()
                .contains("上海");
        assertThat(durationMs)
                .as("单次 IP 解析耗时应在 50ms 以内，当前: %d ms", durationMs)
                .isLessThan(50L);
    }
}