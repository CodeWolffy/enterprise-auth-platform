package com.enterprise.auth.platform.modules.log.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysLogService {

    private final SysLogMapper sysLogMapper;

    public SysLogService(SysLogMapper sysLogMapper) {
        this.sysLogMapper = sysLogMapper;
    }

    public PageResult<SysLogEntity> page(String tenantId, String eventType, String operator, String requestId,
                                         String clientIp, LocalDateTime from, LocalDateTime to, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        LambdaQueryWrapper<SysLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(tenantId), SysLogEntity::getTenantId, tenantId)
                .like(StringUtils.hasText(eventType), SysLogEntity::getEventType, eventType)
                .like(StringUtils.hasText(operator), SysLogEntity::getOperator, operator)
                .like(StringUtils.hasText(requestId), SysLogEntity::getRequestId, requestId)
                .like(StringUtils.hasText(clientIp), SysLogEntity::getClientIp, clientIp)
                .ge(from != null, SysLogEntity::getCreatedAt, from)
                .le(to != null, SysLogEntity::getCreatedAt, to)
                .orderByDesc(SysLogEntity::getCreatedAt);
        Page<SysLogEntity> result = sysLogMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(result.getTotal(), safePage, safeSize, result.getRecords());
    }

    public long count(String tenantId, String eventType, String operator, LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<SysLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(tenantId), SysLogEntity::getTenantId, tenantId)
                .like(StringUtils.hasText(eventType), SysLogEntity::getEventType, eventType)
                .like(StringUtils.hasText(operator), SysLogEntity::getOperator, operator)
                .ge(from != null, SysLogEntity::getCreatedAt, from)
                .le(to != null, SysLogEntity::getCreatedAt, to);
        return sysLogMapper.selectCount(wrapper);
    }
}