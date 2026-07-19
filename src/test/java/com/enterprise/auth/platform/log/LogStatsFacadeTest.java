package com.enterprise.auth.platform.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.log.application.LogStatsFacade;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.LogDailyAggregateRow;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLogMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
            sysLoginLogMapper
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

    @Test
    void dailyTrendShouldUseTwoAggregateQueries() {
        String today = TimeSupport.today(TimeSupport.DEFAULT_BUSINESS_ZONE).toString();
        LogDailyAggregateRow login = new LogDailyAggregateRow();
        login.setDayKey(today);
        login.setLoginCount(3L);
        login.setLoginFailedCount(1L);
        LogDailyAggregateRow operation = new LogDailyAggregateRow();
        operation.setDayKey(today);
        operation.setOperationCount(7L);
        when(sysLoginLogMapper.selectDailyTrend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of(login));
        when(sysLogMapper.selectDailyTrend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of(operation));

        var trend = facade.dailyTrend("tenant-a", false, Optional.empty());

        assertThat(trend).hasSize(7);
        assertThat(trend.get(6).loginCount()).isEqualTo(3L);
        assertThat(trend.get(6).operationCount()).isEqualTo(7L);
        assertThat(trend.get(6).loginFailedCount()).isEqualTo(1L);
        verify(sysLoginLogMapper).selectDailyTrend(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyList()
        );
        verify(sysLogMapper).selectDailyTrend(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }
}
