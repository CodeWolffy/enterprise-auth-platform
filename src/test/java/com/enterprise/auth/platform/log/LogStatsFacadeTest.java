package com.enterprise.auth.platform.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.enterprise.auth.platform.modules.auth.application.DataScopeService;
import com.enterprise.auth.platform.modules.auth.application.PlatformAdminSupport;
import com.enterprise.auth.platform.modules.log.application.LogStatsFacade;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLogMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LogStatsFacadeTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "log-test"), SysLogEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "login-log-test"), SysLoginLogEntity.class);
    }

    private final SysLogMapper sysLogMapper = mock(SysLogMapper.class);
    private final SysLoginLogMapper sysLoginLogMapper = mock(SysLoginLogMapper.class);
    private final LogStatsFacade facade = new LogStatsFacade(
            sysLogMapper,
            sysLoginLogMapper,
            mock(DataScopeService.class),
            mock(PlatformAdminSupport.class)
    );

    @Test
    void emptyVisibleUserSetMustDenyOperationLogs() {
        facade.countOperationLogs("tenant-a", false, Optional.of(Set.of()), false);

        ArgumentCaptor<LambdaQueryWrapper<SysLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysLogMapper).selectCount(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("1 = 0");
    }

    @Test
    void emptyVisibleUserSetMustDenyLoginLogs() {
        facade.countLoginLogs("tenant-a", false, Optional.of(Set.of()), null, null);

        ArgumentCaptor<LambdaQueryWrapper<SysLoginLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysLoginLogMapper).selectCount(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("1 = 0");
    }
}
