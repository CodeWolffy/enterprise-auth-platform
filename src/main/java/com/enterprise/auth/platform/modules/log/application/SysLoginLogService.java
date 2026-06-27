package com.enterprise.auth.platform.modules.log.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysLoginLogService {

    private final SysLoginLogMapper sysLoginLogMapper;

    public SysLoginLogService(SysLoginLogMapper sysLoginLogMapper) {
        this.sysLoginLogMapper = sysLoginLogMapper;
    }

    public PageResult<SysLoginLogEntity> page(String tenantId, String userName, String status,
                                              String clientIp, LocalDateTime from, LocalDateTime to,
                                              int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
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
        return PageResult.of(result.getTotal(), safePage, safeSize, result.getRecords());
    }

    public long count(String tenantId, String userName, String status, LocalDateTime from, LocalDateTime to) {
        String effectiveTenantId = effectiveTenantId(tenantId);
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(effectiveTenantId), SysLoginLogEntity::getTenantId, effectiveTenantId)
                .like(StringUtils.hasText(userName), SysLoginLogEntity::getUserName, userName)
                .eq(StringUtils.hasText(status), SysLoginLogEntity::getStatus, status)
                .ge(from != null, SysLoginLogEntity::getCreatedAt, from)
                .le(to != null, SysLoginLogEntity::getCreatedAt, to);
        return sysLoginLogMapper.selectCount(wrapper);
    }

    private String effectiveTenantId(String requestedTenantId) {
        if (StringUtils.hasText(requestedTenantId)) {
            return requestedTenantId.trim();
        }
        return TenantContext.isGlobalScope() ? null : TenantContext.getTenantId();
    }
}