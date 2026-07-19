package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.outbox.OutboxEventPublisher;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.tenant.api.TenantPackageMenuSyncEvent;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageMapper;
import com.enterprise.auth.platform.modules.tenant.interfaces.TenantPackageCrudRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TenantCatalogManagementService {

    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysTenantMapper sysTenantMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;

    public TenantCatalogManagementService(
            SysTenantPackageMapper sysTenantPackageMapper,
            SysTenantMapper sysTenantMapper,
            OutboxEventPublisher outboxEventPublisher,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService
    ) {
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.outboxEventPublisher = outboxEventPublisher;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
    }

    public List<TenantPackageView> packages() {
        requirePlatformTenant();
        var references = loadPackageReferences();
        return sysTenantPackageMapper.selectList(new LambdaQueryWrapper<SysTenantPackageEntity>()
                        .eq(SysTenantPackageEntity::getTenantId, "platform")
                        .eq(SysTenantPackageEntity::getDeleted, 0)
                        .orderByAsc(SysTenantPackageEntity::getOrderNo)
                        .orderByAsc(SysTenantPackageEntity::getId))
                .stream()
                .map(item -> toPackageView(item, references.get(item.getPackageCode())))
                .toList();
    }

    public TenantPackageImpactView packageImpact(Long id) {
        requirePlatformTenant();
        SysTenantPackageEntity entity = getPackage(id);
        PackageReferenceHint reference = loadPackageReferences().get(entity.getPackageCode());
        int referencedTenantCount = reference == null ? 0 : reference.referencedTenantCount();
        List<String> referencedTenantIds = reference == null ? List.of() : reference.sampleTenantIds();
        List<ImpactRuleView> rules = new ArrayList<>();
        rules.add(new ImpactRuleView(
                "PACKAGE_REFERENCED_TENANTS",
                "WARN",
                referencedTenantCount > 0,
                "套餐变更会重建引用租户的菜单范围，并清理越界角色授权。",
                referencedTenantCount,
                false
        ));
        rules.add(new ImpactRuleView(
                "PACKAGE_EMPTY_APP_KEY",
                "WARN",
                !StringUtils.hasText(entity.getAppKey()),
                "套餐未配置应用标识，新租户将没有默认菜单。",
                StringUtils.hasText(entity.getAppKey()) ? 1 : 0,
                false
        ));
        return new TenantPackageImpactView(
                entity.getId(),
                entity.getPackageCode(),
                entity.getPackageName(),
                normalizeStatus(entity.getStatus()),
                entity.getAppKey(),
                referencedTenantCount,
                referencedTenantIds,
                rules,
                referencedTenantCount > 0
                        ? List.of("保存套餐后系统会自动同步引用租户菜单，并失效权限快照。")
                        : List.of("当前未发现引用租户，可直接调整。")
        );
    }

    @Transactional
    public TenantPackageView createPackage(TenantPackageCrudRequest request) {
        requirePlatformTenant();
        String packageCode = normalizeCode(request.packageCode());
        if (packageExists(packageCode, null)) {
            throw new BusinessException("套餐编码已存在");
        }
        SysTenantPackageEntity entity = new SysTenantPackageEntity();
        apply(entity, request);
        entity.setTenantId("platform");
        sysTenantPackageMapper.insert(entity);
        evictPrincipalSnapshots();
        return toPackageView(entity, loadPackageReferences().get(packageCode));
    }

    @Transactional
    public TenantPackageView updatePackage(Long id, TenantPackageCrudRequest request) {
        requirePlatformTenant();
        SysTenantPackageEntity entity = getPackage(id);
        String oldCode = entity.getPackageCode();
        String packageCode = normalizeCode(request.packageCode());
        if (packageExists(packageCode, id)) {
            throw new BusinessException("套餐编码已存在");
        }
        apply(entity, request);
        sysTenantPackageMapper.updateById(entity);
        if (!oldCode.equals(packageCode)) {
            migratePackageReference(oldCode, packageCode);
        }
        enqueuePackageTenantMenuSync(packageCode);
        evictPrincipalSnapshots();
        return toPackageView(entity, loadPackageReferences().get(packageCode));
    }

    @Transactional
    public void deletePackage(Long id) {
        requirePlatformTenant();
        SysTenantPackageEntity entity = getPackage(id);
        PackageReferenceHint reference = loadPackageReferences().get(entity.getPackageCode());
        if (reference != null && reference.referencedTenantCount() > 0) {
            throw new BusinessException("该套餐仍被租户使用，暂不允许删除");
        }
        sysTenantPackageMapper.deleteById(id);
        evictPrincipalSnapshots();
    }

    private void enqueuePackageTenantMenuSync(String packageCode) {
        if (!StringUtils.hasText(packageCode)) {
            return;
        }
        outboxEventPublisher.enqueue(
                TenantPackageMenuSyncEvent.TYPE,
                "platform",
                TenantPackageMenuSyncEvent.AGGREGATE_TYPE,
                packageCode,
                new TenantPackageMenuSyncEvent(packageCode)
        );
    }

    private java.util.Map<String, PackageReferenceHint> loadPackageReferences() {
        java.util.Map<String, Set<String>> tenantIdsByPackageCode = new java.util.LinkedHashMap<>();
        for (SysTenantEntity tenant : sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .orderByAsc(SysTenantEntity::getId))) {
            if (!StringUtils.hasText(tenant.getPackageCode()) || !StringUtils.hasText(tenant.getTenantId())) {
                continue;
            }
            tenantIdsByPackageCode
                    .computeIfAbsent(tenant.getPackageCode().trim(), ignored -> new LinkedHashSet<>())
                    .add(tenant.getTenantId().trim());
        }
        java.util.Map<String, PackageReferenceHint> result = new java.util.LinkedHashMap<>();
        for (var entry : tenantIdsByPackageCode.entrySet()) {
            result.put(entry.getKey(), new PackageReferenceHint(entry.getValue().size(), entry.getValue().stream().limit(5).toList()));
        }
        return result;
    }

    private void migratePackageReference(String oldCode, String newCode) {
        sysTenantMapper.updatePackageCodeReferences(oldCode, newCode);
    }

    private boolean packageExists(String packageCode, Long excludeId) {
        return sysTenantPackageMapper.selectCount(new LambdaQueryWrapper<SysTenantPackageEntity>()
                .eq(SysTenantPackageEntity::getTenantId, "platform")
                .eq(SysTenantPackageEntity::getPackageCode, packageCode)
                .eq(SysTenantPackageEntity::getDeleted, 0)
                .ne(excludeId != null, SysTenantPackageEntity::getId, excludeId)) > 0;
    }

    private SysTenantPackageEntity getPackage(Long id) {
        SysTenantPackageEntity entity = sysTenantPackageMapper.selectOne(new LambdaQueryWrapper<SysTenantPackageEntity>()
                .eq(SysTenantPackageEntity::getId, id)
                .eq(SysTenantPackageEntity::getTenantId, "platform")
                .eq(SysTenantPackageEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("租户套餐不存在");
        }
        return entity;
    }

    private void apply(SysTenantPackageEntity entity, TenantPackageCrudRequest request) {
        entity.setPackageCode(normalizeCode(request.packageCode()));
        entity.setPackageName(request.packageName().trim());
        entity.setSubtitle(blankToNull(request.subtitle()));
        entity.setSalesPrice(request.salesPrice());
        entity.setOriginalPrice(request.originalPrice());
        entity.setDescriptionMd(blankToNull(request.descriptionMd()));
        entity.setAppKey(blankToNull(request.appKey()));
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setPackageDesc(blankToNull(request.packageDesc()));
        entity.setStatus(normalizeStatus(request.status()));
    }

    private TenantPackageView toPackageView(SysTenantPackageEntity entity, PackageReferenceHint referenceHint) {
        int referencedTenantCount = referenceHint == null ? 0 : referenceHint.referencedTenantCount();
        List<String> referencedTenantIds = referenceHint == null ? List.of() : referenceHint.sampleTenantIds();
        return new TenantPackageView(
                entity.getId(),
                entity.getPackageCode(),
                entity.getPackageName(),
                entity.getSubtitle(),
                entity.getSalesPrice(),
                entity.getOriginalPrice(),
                entity.getDescriptionMd(),
                entity.getAppKey(),
                entity.getOrderNo(),
                entity.getPackageDesc(),
                normalizeStatus(entity.getStatus()),
                entity.getUpdatedAt() == null ? entity.getCreatedAt() : entity.getUpdatedAt(),
                referencedTenantCount,
                referencedTenantIds
        );
    }

    private void requirePlatformTenant() {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId) && !"platform".equals(tenantId)) {
            throw new BusinessException("仅平台租户允许维护租户套餐");
        }
    }

    private void evictPrincipalSnapshots() {
        permissionSnapshotInvalidationService.invalidateAll();
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeStatus(String value) {
        return "1".equals(value) ? "1" : "0";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Schema(description = "租户套餐视图")
    public record TenantPackageView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "运营副标题") String subtitle,
            @Schema(description = "销售价") BigDecimal salesPrice,
            @Schema(description = "原价") BigDecimal originalPrice,
            @Schema(description = "富文本或 Markdown 描述") String descriptionMd,
            @Schema(description = "应用标识，多个值用逗号分隔") String appKey,
            @Schema(description = "展示排序") Integer orderNo,
            @Schema(description = "套餐说明") String packageDesc,
            @Schema(description = "状态：0 正常，1 停用") String status,
            @Schema(description = "更新时间，ISO-8601 UTC") java.time.Instant updatedAt,
            @Schema(description = "引用该套餐的租户数量") int referencedTenantCount,
            @Schema(description = "引用该套餐的租户示例（最多 5 条）") List<String> referencedTenantIds
    ) {
    }

    @Schema(description = "套餐变更影响分析")
    public record TenantPackageImpactView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "状态：0 正常，1 停用") String status,
            @Schema(description = "应用标识") String appKey,
            @Schema(description = "引用租户数量") int referencedTenantCount,
            @Schema(description = "引用租户示例（最多 5 条）") List<String> referencedTenantIds,
            @Schema(description = "命中的规则列表") List<ImpactRuleView> rules,
            @Schema(description = "建议操作") List<String> recommendedActions
    ) {
    }

    @Schema(description = "影响规则命中结果")
    public record ImpactRuleView(
            @Schema(description = "规则编码") String ruleCode,
            @Schema(description = "规则等级：ERROR/WARN") String level,
            @Schema(description = "是否命中") boolean hit,
            @Schema(description = "规则说明") String message,
            @Schema(description = "关联数量") int relatedCount,
            @Schema(description = "是否阻断删除等高风险操作") boolean blocking
    ) {
    }

    private record PackageReferenceHint(int referencedTenantCount, List<String> sampleTenantIds) {
    }
}
