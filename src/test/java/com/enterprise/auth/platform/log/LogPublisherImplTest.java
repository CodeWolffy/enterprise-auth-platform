package com.enterprise.auth.platform.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.enterprise.auth.platform.common.web.ClientIpResolver;
import com.enterprise.auth.platform.common.web.IpLocationResolver;
import com.enterprise.auth.platform.modules.log.application.LogPublisherImpl;
import com.enterprise.auth.platform.modules.log.domain.event.LogEvent;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LogPublisherImplTest {

    @Test
    void shouldRedactSensitiveQueryParametersFromColumnsAndPayload() {
        SysLogMapper logMapper = mock(SysLogMapper.class);
        LogPublisherImpl publisher = new LogPublisherImpl(
                logMapper,
                mock(SysLoginLogMapper.class),
                new ObjectMapper(),
                mock(ClientIpResolver.class),
                mock(IpLocationResolver.class)
        );
        String query = "page=1&token=raw-token&password=p%40ss&keyword=user";
        LogEvent event = new LogEvent(
                "QUERY", "alice", "tenant-a", Map.of("requestParams", query),
                "request-1", "198.51.100.24", null, "GET", "/api/users", query,
                12L, "1", null
        );

        publisher.publish(event);

        ArgumentCaptor<SysLogEntity> captor = ArgumentCaptor.forClass(SysLogEntity.class);
        verify(logMapper).insert(captor.capture());
        SysLogEntity entity = captor.getValue();
        assertThat(entity.getRequestParams())
                .isEqualTo("page=1&token=******&password=******&keyword=user");
        assertThat(entity.getPayloadJson()).doesNotContain("raw-token", "p%40ss");
        assertThat(entity.getPayloadJson()).contains("token=******", "password=******");
    }
}
