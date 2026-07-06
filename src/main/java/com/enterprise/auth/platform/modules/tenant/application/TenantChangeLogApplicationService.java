package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantChangeLogEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantChangeLogMapper;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
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

    public PageResult<TenantChangeView> history(
            String tenantId,
            String changeType,
            String fieldKey,
            String operator,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        tenantAccessPolicy.ensureTenantReadable(tenantId);
        int safePage = PaginationSupport.normalizePage(page);
        int safeSize = PaginationSupport.normalizeSize(size);
        LambdaQueryWrapper<SysTenantChangeLogEntity> query = buildHistoryQuery(
                tenantId, changeType, fieldKey, operator, from, to
        ).orderByDesc(SysTenantChangeLogEntity::getOccurredAt)
                .orderByDesc(SysTenantChangeLogEntity::getId);
        long total = sysTenantChangeLogMapper.selectCount(query);
        if (total == 0) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }
        int offset = (safePage - 1) * safeSize;
        List<TenantChangeView> records = sysTenantChangeLogMapper.selectList(query.last("limit " + offset + "," + safeSize))
                .stream()
                .map(this::toChangeView)
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    public TenantHistorySummaryView historySummary(
            String tenantId,
            String changeType,
            String fieldKey,
            String operator,
            Instant from,
            Instant to
    ) {
        tenantAccessPolicy.ensureTenantReadable(tenantId);
        List<SysTenantChangeLogEntity> records = sysTenantChangeLogMapper.selectList(
                buildHistoryQuery(tenantId, changeType, fieldKey, operator, from, to)
                        .orderByDesc(SysTenantChangeLogEntity::getOccurredAt)
                        .orderByDesc(SysTenantChangeLogEntity::getId)
        );
        long packageChanges = records.stream().filter(item -> "PACKAGE".equals(item.getChangeType())).count();
        long menuChanges = records.stream().filter(item -> "MENU".equals(item.getChangeType())).count();
        long statusChanges = records.stream().filter(item -> "STATUS".equals(item.getChangeType())).count();
        long profileChanges = records.stream().filter(item -> "PROFILE".equals(item.getChangeType())).count();
        List<TenantChangeView> recentTimeline = records.stream()
                .limit(8)
                .map(this::toChangeView)
                .toList();
        Map<String, Long> affectedFieldCounts = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getFieldKey() == null ? "unknown" : item.getFieldKey(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        return new TenantHistorySummaryView(
                tenantId,
                records.size(),
                packageChanges,
                menuChanges,
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
        entity.setOccurredAt(TimeSupport.now());
        sysTenantChangeLogMapper.insert(entity);
    }

    private LambdaQueryWrapper<SysTenantChangeLogEntity> buildHistoryQuery(
            String tenantId,
            String changeType,
            String fieldKey,
            String operator,
            Instant from,
            Instant to
    ) {
        return new LambdaQueryWrapper<SysTenantChangeLogEntity>()
                .eq(SysTenantChangeLogEntity::getTenantId, tenantId)
                .eq(StringUtils.hasText(changeType), SysTenantChangeLogEntity::getChangeType, changeType)
                .eq(StringUtils.hasText(fieldKey), SysTenantChangeLogEntity::getFieldKey, fieldKey)
                .like(StringUtils.hasText(operator), SysTenantChangeLogEntity::getOperator, operator)
                .ge(from != null, SysTenantChangeLogEntity::getOccurredAt, from)
                .lt(to != null, SysTenantChangeLogEntity::getOccurredAt, to);
    }

    private TenantChangeView toChangeView(SysTenantChangeLogEntity item) {
        return new TenantChangeView(
                item.getId(),
                item.getTenantId(),
                item.getChangeType(),
                item.getFieldKey(),
                item.getOldValue(),
                item.getNewValue(),
                item.getSummary(),
                buildImpactSummary(item),
                item.getOperator(),
                item.getOccurredAt()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildImpactSummary(SysTenantChangeLogEntity entity) {
        String fieldKey = entity.getFieldKey();
        if ("packageCode".equals(fieldKey) || "packageName".equals(fieldKey)) {
            return "套餐变更会重建当前租户的默认菜单范围和运营策略展示。";
        }
        if ("menuIds".equals(fieldKey)) {
            return "菜单范围变更会影响当前租户可见模块与角色可授权范围。";
        }
        if ("tenantStatus".equals(fieldKey)) {
            return "租户状态变更会直接影响登录、访问和管理操作可用性。";
        }
        if ("authBeginAt".equals(fieldKey) || "expireAt".equals(fieldKey)) {
            return "授权期限变更会影响租户登录、切换、续费提醒和运营排期。";
        }
        if ("logoUrl".equals(fieldKey) || "contactName".equals(fieldKey)
                || "contactPhone".equals(fieldKey) || "contactEmail".equals(fieldKey)
                || "website".equals(fieldKey) || "address".equals(fieldKey)) {
            return "租户基础资料变更会影响管理端展示、交付沟通和运营联系信息。";
        }
        if ("lifecycleNote".equals(fieldKey)) {
            return "运营备注变更会影响租户交付说明、排期提示和内部协作口径。";
        }
        return null;
    }
    @Schema(description = "租户变更记录")
    public record TenantChangeView(
            @Schema(description = "记录 ID") Long id,
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "变更类型") String changeType,
            @Schema(description = "字段键") String fieldKey,
            @Schema(description = "旧值") String oldValue,
            @Schema(description = "新值") String newValue,
            @Schema(description = "变更摘要") String summary,
            @Schema(description = "影响说明") String impactSummary,
            @Schema(description = "操作人") String operator,
            @Schema(description = "变更时间，ISO-8601 UTC") Instant occurredAt
    ) {
    }

    @Schema(description = "租户变更历史摘要")
    public record TenantHistorySummaryView(
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "命中的变更总数") Integer totalChanges,
            @Schema(description = "套餐变更数") Long packageChanges,
            @Schema(description = "菜单授权变更数") Long menuChanges,
            @Schema(description = "状态变更数") Long statusChanges,
            @Schema(description = "资料变更数") Long profileChanges,
            @Schema(description = "字段影响分布") Map<String, Long> affectedFieldCounts,
            @Schema(description = "最近轨迹") List<TenantChangeView> recentTimeline
    ) {
    }
}
