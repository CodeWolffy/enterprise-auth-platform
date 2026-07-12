package com.enterprise.auth.platform.modules.log.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysLoginLogService {

    private final SysLoginLogMapper sysLoginLogMapper;

    public SysLoginLogService(SysLoginLogMapper sysLoginLogMapper) {
        this.sysLoginLogMapper = sysLoginLogMapper;
    }

    public PageResult<LoginLogView> page(
            String tenantId,
            String userName,
            String status,
            String clientIp,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        int safePage = PaginationSupport.normalizePage(page);
        int safeSize = PaginationSupport.normalizeSize(size, 200);
        String effectiveTenantId = effectiveTenantId(tenantId);
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(effectiveTenantId), SysLoginLogEntity::getTenantId, effectiveTenantId)
                .like(StringUtils.hasText(userName), SysLoginLogEntity::getUserName, userName)
                .eq(StringUtils.hasText(status), SysLoginLogEntity::getStatus, status)
                .like(StringUtils.hasText(clientIp), SysLoginLogEntity::getIpAddr, clientIp)
                .ge(from != null, SysLoginLogEntity::getCreatedAt, from)
                .le(to != null, SysLoginLogEntity::getCreatedAt, to)
                .orderByDesc(SysLoginLogEntity::getCreatedAt);
        Page<SysLoginLogEntity> result = sysLoginLogMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(
                result.getTotal(),
                safePage,
                safeSize,
                result.getRecords().stream().map(this::toView).toList()
        );
    }

    public long count(String tenantId, String userName, String status, Instant from, Instant to) {
        String effectiveTenantId = effectiveTenantId(tenantId);
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(effectiveTenantId), SysLoginLogEntity::getTenantId, effectiveTenantId)
                .like(StringUtils.hasText(userName), SysLoginLogEntity::getUserName, userName)
                .eq(StringUtils.hasText(status), SysLoginLogEntity::getStatus, status)
                .ge(from != null, SysLoginLogEntity::getCreatedAt, from)
                .le(to != null, SysLoginLogEntity::getCreatedAt, to);
        return sysLoginLogMapper.selectCount(wrapper);
    }

    private LoginLogView toView(SysLoginLogEntity entity) {
        return new LoginLogView(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserName(),
                entity.getStatus(),
                entity.getIpAddr(),
                entity.getLocation(),
                entity.getBrowser(),
                entity.getOs(),
                entity.getMsg(),
                entity.getCreatedAt()
        );
    }

    private String effectiveTenantId(String requestedTenantId) {
        if (StringUtils.hasText(requestedTenantId)) {
            return requestedTenantId.trim();
        }
        return TenantContext.isGlobalScope() ? null : TenantContext.getTenantId();
    }
}