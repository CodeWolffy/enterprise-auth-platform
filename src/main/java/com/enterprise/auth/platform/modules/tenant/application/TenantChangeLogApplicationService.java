package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.dao.entity.SysTenantChangeLogEntity;
import com.enterprise.auth.platform.dao.mapper.SysTenantChangeLogMapper;
import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.service.TenantManagementService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantChangeLogApplicationService {

    private final SysTenantChangeLogMapper sysTenantChangeLogMapper;
    private final TenantAccessPolicy tenantAccessPolicy;

    public TenantChangeLogApplicationService(
            SysTenantChangeLogMapper sysTenantChangeLogMapper,
            TenantAccessPolicy tenantAccessPolicy
    ) {
        this.sysTenantChangeLogMapper = sysTenantChangeLogMapper;
        this.tenantAccessPolicy = tenantAccessPolicy;
    }

    public PageResult<TenantManagementService.TenantChangeView> history(
            String tenantId,
            String changeType,
            String fieldKey,
            String operator,
            Long fromEpochMs,
            Long toEpochMs,
            int page,
            int size
    ) {
        tenantAccessPolicy.ensureTenantReadable(tenantId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        LambdaQueryWrapper<SysTenantChangeLogEntity> query = buildHistoryQuery(
                tenantId, changeType, fieldKey, operator, fromEpochMs, toEpochMs
        ).orderByDesc(SysTenantChangeLogEntity::getOccurredAt)
                .orderByDesc(SysTenantChangeLogEntity::getId);
        long total = sysTenantChangeLogMapper.selectCount(query);
        if (total == 0) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }
        int offset = (safePage - 1) * safeSize;
        List<TenantManagementService.TenantChangeView> records = sysTenantChangeLogMapper.selectList(query.last("limit " + offset + "," + safeSize))
                .stream()
                .map(this::toChangeView)
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    public TenantManagementService.TenantHistorySummaryView historySummary(
            String tenantId,
            String changeType,
            String fieldKey,
            String operator,
            Long fromEpochMs,
            Long toEpochMs
    ) {
        tenantAccessPolicy.ensureTenantReadable(tenantId);
        List<SysTenantChangeLogEntity> records = sysTenantChangeLogMapper.selectList(
                buildHistoryQuery(tenantId, changeType, fieldKey, operator, fromEpochMs, toEpochMs)
                        .orderByDesc(SysTenantChangeLogEntity::getOccurredAt)
                        .orderByDesc(SysTenantChangeLogEntity::getId)
        );
        long packageChanges = records.stream().filter(item -> "PACKAGE".equals(item.getChangeType())).count();
        long capabilityChanges = records.stream().filter(item -> "CAPABILITY".equals(item.getChangeType())).count();
        long statusChanges = records.stream().filter(item -> "STATUS".equals(item.getChangeType())).count();
        long profileChanges = records.stream().filter(item -> "PROFILE".equals(item.getChangeType())).count();
        List<TenantManagementService.TenantChangeView> recentTimeline = records.stream()
                .limit(8)
                .map(this::toChangeView)
                .toList();
        Map<String, Long> affectedFieldCounts = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getFieldKey() == null ? "unknown" : item.getFieldKey(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        return new TenantManagementService.TenantHistorySummaryView(
                tenantId,
                records.size(),
                packageChanges,
                capabilityChanges,
                statusChanges,
                profileChanges,
                affectedFieldCounts,
                recentTimeline
        );
    }

    public void recordIfChanged(
            String tenantId,
            String changeType,
            String fieldKey,
            String oldValue,
            String newValue,
            String summary,
            String operator
    ) {
        if (java.util.Objects.equals(trimToNull(oldValue), trimToNull(newValue))) {
            return;
        }
        recordTenantChange(tenantId, changeType, fieldKey, oldValue, newValue, summary, operator);
    }

    public void recordTenantChange(
            String tenantId,
            String changeType,
            String fieldKey,
            String oldValue,
            String newValue,
            String summary,
            String operator
    ) {
        SysTenantChangeLogEntity entity = new SysTenantChangeLogEntity();
        entity.setTenantId(tenantId);
        entity.setChangeType(changeType);
        entity.setFieldKey(fieldKey);
        entity.setOldValue(trimToNull(oldValue));
        entity.setNewValue(trimToNull(newValue));
        entity.setSummary(summary);
        entity.setOperator(operator);
        entity.setOccurredAt(TimeSupport.utcNowDateTime());
        sysTenantChangeLogMapper.insert(entity);
    }

    private LambdaQueryWrapper<SysTenantChangeLogEntity> buildHistoryQuery(
            String tenantId,
            String changeType,
            String fieldKey,
            String operator,
            Long fromEpochMs,
            Long toEpochMs
    ) {
        return new LambdaQueryWrapper<SysTenantChangeLogEntity>()
                .eq(SysTenantChangeLogEntity::getTenantId, tenantId)
                .eq(StringUtils.hasText(changeType), SysTenantChangeLogEntity::getChangeType, changeType)
                .eq(StringUtils.hasText(fieldKey), SysTenantChangeLogEntity::getFieldKey, fieldKey)
                .like(StringUtils.hasText(operator), SysTenantChangeLogEntity::getOperator, operator)
                .ge(fromEpochMs != null, SysTenantChangeLogEntity::getOccurredAt,
                        fromEpochMs == null ? null : TimeSupport.localDateTimeFromEpochMilli(fromEpochMs))
                .lt(toEpochMs != null, SysTenantChangeLogEntity::getOccurredAt,
                        toEpochMs == null ? null : TimeSupport.localDateTimeFromEpochMilli(toEpochMs));
    }

    private TenantManagementService.TenantChangeView toChangeView(SysTenantChangeLogEntity item) {
        return new TenantManagementService.TenantChangeView(
                item.getId(),
                item.getTenantId(),
                item.getChangeType(),
                item.getFieldKey(),
                item.getOldValue(),
                item.getNewValue(),
                item.getSummary(),
                buildImpactSummary(item),
                item.getOperator(),
                TimeSupport.toEpochMilli(item.getOccurredAt())
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildImpactSummary(SysTenantChangeLogEntity entity) {
        String fieldKey = entity.getFieldKey();
        if ("packageCode".equals(fieldKey) || "packageName".equals(fieldKey)) {
            return "套餐变更会影响当前租户的默认能力集、配额说明和运营策略展示。";
        }
        if ("userQuota".equals(fieldKey)) {
            return "用户配额变更会影响新增用户容量和租户运营阈值。";
        }
        if ("storageQuotaGb".equals(fieldKey)) {
            return "存储配额变更会影响文件容量规划与对象存储成本预估。";
        }
        if ("capabilityCodes".equals(fieldKey)) {
            return "能力范围变更会影响当前租户可见模块与功能开关。";
        }
        if ("capabilityOverrides".equals(fieldKey)) {
            return "能力覆盖变更会在套餐默认能力之外，单独调整当前租户的生效状态和说明文案。";
        }
        if ("tenantStatus".equals(fieldKey)) {
            return "租户状态变更会直接影响登录、访问和管理操作可用性。";
        }
        if ("expireAt".equals(fieldKey)) {
            return "到期时间变更会影响续费提醒、停用策略和运营排期。";
        }
        if ("lifecycleNote".equals(fieldKey)) {
            return "运营备注变更会影响租户交付说明、排期提示和内部协作口径。";
        }
        return null;
    }
}