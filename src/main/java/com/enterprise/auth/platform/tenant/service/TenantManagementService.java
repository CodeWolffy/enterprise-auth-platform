package com.enterprise.auth.platform.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.entity.SysConfigEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantChangeLogEntity;
import com.enterprise.auth.platform.persistence.mapper.SysConfigMapper;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantChangeLogMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.dto.CreateTenantRequest;
import com.enterprise.auth.platform.tenant.dto.UpdateTenantRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TenantManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysTenantMapper sysTenantMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysTenantChangeLogMapper sysTenantChangeLogMapper;
    private final CatalogService catalogService;
    private final AuditService auditService;

    public TenantManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysTenantMapper sysTenantMapper,
            @Nullable SysUserMapper sysUserMapper,
            @Nullable SysRoleMapper sysRoleMapper,
            @Nullable SysDeptMapper sysDeptMapper,
            @Nullable SysConfigMapper sysConfigMapper,
            @Nullable SysTenantChangeLogMapper sysTenantChangeLogMapper,
            CatalogService catalogService,
            AuditService auditService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysTenantMapper = sysTenantMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.sysTenantChangeLogMapper = sysTenantChangeLogMapper;
        this.catalogService = catalogService;
        this.auditService = auditService;
    }

    @Transactional
    public CatalogService.TenantView create(CreateTenantRequest request) {
        requireDatabaseMode();
        String operator = SecuritySupport.currentOperator();
        if (existsTenant(request.tenantId())) {
            throw new BusinessException("租户标识已存在");
        }

        SysTenantEntity entity = new SysTenantEntity();
        entity.setTenantId(request.tenantId());
        entity.setTenantName(request.tenantName());
        entity.setPlatformLevel(request.platformLevel() ? 1 : 0);
        entity.setTenantStatus(request.tenantStatus() == null ? 1 : request.tenantStatus());
        entity.setExpireAt(request.expireAt());
        sysTenantMapper.insert(entity);
        saveTenantProfile(request.tenantId(), request.packageCode(), request.packageName(), request.userQuota(),
                request.storageQuotaGb(), request.capabilityCodes(), request.lifecycleNote());
        recordTenantChange(request.tenantId(), "CREATED", "tenant", null, request.tenantName(), "创建租户", operator);
        recordTenantChange(request.tenantId(), "STATUS", "tenantStatus", null,
                String.valueOf(entity.getTenantStatus()), "初始化租户状态", operator);
        recordTenantChange(request.tenantId(), "PACKAGE", "packageCode", null,
                request.packageCode(), "初始化租户套餐", operator);

        auditService.record("TENANT_CREATED", operator, request.tenantId(), Map.of("tenantId", request.tenantId()));
        return catalogService.tenant(request.tenantId());
    }

    @Transactional
    public CatalogService.TenantView update(String tenantId, UpdateTenantRequest request) {
        requireDatabaseMode();
        SysTenantEntity entity = getTenant(tenantId);
        String oldTenantName = entity.getTenantName();
        Integer oldTenantStatus = entity.getTenantStatus();
        java.time.LocalDateTime oldExpireAt = entity.getExpireAt();
        Map<String, String> oldProfile = loadTenantProfileValues(List.of(tenantId)).getOrDefault(tenantId, Map.of());
        entity.setTenantName(request.tenantName());
        entity.setPlatformLevel(request.platformLevel() ? 1 : 0);
        if (request.tenantStatus() != null) {
            entity.setTenantStatus(request.tenantStatus());
        }
        entity.setExpireAt(request.expireAt());
        sysTenantMapper.updateById(entity);
        saveTenantProfile(tenantId, request.packageCode(), request.packageName(), request.userQuota(),
                request.storageQuotaGb(), request.capabilityCodes(), request.lifecycleNote());

        String operator = SecuritySupport.currentOperator();
        recordIfChanged(tenantId, "PROFILE", "tenantName", oldTenantName, request.tenantName(), "更新租户名称", operator);
        recordIfChanged(tenantId, "STATUS", "tenantStatus", toStringValue(oldTenantStatus), toStringValue(entity.getTenantStatus()), "更新租户状态", operator);
        recordIfChanged(tenantId, "PROFILE", "expireAt", toStringValue(oldExpireAt), toStringValue(request.expireAt()), "更新到期时间", operator);
        recordIfChanged(tenantId, "PACKAGE", "packageCode", oldProfile.get("tenant.package.code"), request.packageCode(), "更新租户套餐编码", operator);
        recordIfChanged(tenantId, "PACKAGE", "packageName", oldProfile.get("tenant.package.name"), request.packageName(), "更新租户套餐名称", operator);
        recordIfChanged(tenantId, "PACKAGE", "userQuota", oldProfile.get("tenant.quota.users"), toStringValue(request.userQuota()), "更新用户配额", operator);
        recordIfChanged(tenantId, "PACKAGE", "storageQuotaGb", oldProfile.get("tenant.quota.storage_gb"), toStringValue(request.storageQuotaGb()), "更新存储配额", operator);
        recordIfChanged(tenantId, "CAPABILITY", "capabilityCodes", oldProfile.get("tenant.capability.codes"),
                request.capabilityCodes() == null ? null : String.join(",", request.capabilityCodes()), "更新租户能力范围", operator);
        recordIfChanged(tenantId, "PROFILE", "lifecycleNote", oldProfile.get("tenant.lifecycle.note"), request.lifecycleNote(), "更新运营备注", operator);

        auditService.record("TENANT_UPDATED", operator, tenantId, Map.of("tenantId", tenantId));
        return catalogService.tenant(tenantId);
    }

    @Transactional
    public void delete(String tenantId) {
        requireDatabaseMode();
        String operator = SecuritySupport.currentOperator();
        SysTenantEntity entity = getTenant(tenantId);
        if ((sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getTenantId, tenantId).eq(SysUserEntity::getDeleted, 0)) > 0)
                || (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRoleEntity>().eq(SysRoleEntity::getTenantId, tenantId).eq(SysRoleEntity::getDeleted, 0)) > 0)
                || (sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>().eq(SysDeptEntity::getTenantId, tenantId).eq(SysDeptEntity::getDeleted, 0)) > 0)) {
            throw new BusinessException("租户下仍存在用户、角色或部门数据，暂不允许删除");
        }

        sysTenantMapper.deleteById(entity.getId());
        if (sysConfigMapper != null) {
            sysConfigMapper.delete(new LambdaQueryWrapper<SysConfigEntity>()
                    .eq(SysConfigEntity::getTenantId, tenantId)
                    .in(SysConfigEntity::getConfigKey,
                            "tenant.package.code",
                            "tenant.package.name",
                            "tenant.quota.users",
                            "tenant.quota.storage_gb",
                            "tenant.capability.codes",
                            "tenant.lifecycle.note"));
        }
        recordTenantChange(tenantId, "DELETED", "tenant", entity.getTenantName(), null, "删除租户", operator);
        auditService.record("TENANT_DELETED", operator, tenantId, Map.of("tenantId", entity.getTenantId()));
    }

    public PageResult<CatalogService.TenantView> page(String keyword, Boolean platformLevel, Integer tenantStatus, int page, int size) {
        requireDatabaseMode();
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        LambdaQueryWrapper<SysTenantEntity> query = new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .eq(platformLevel != null, SysTenantEntity::getPlatformLevel, Boolean.TRUE.equals(platformLevel) ? 1 : 0)
                .eq(tenantStatus != null, SysTenantEntity::getTenantStatus, tenantStatus)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysTenantEntity::getTenantId, keyword)
                        .or()
                        .like(SysTenantEntity::getTenantName, keyword));
        long total = sysTenantMapper.selectCount(query);
        if (total == 0) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }
        int offset = (safePage - 1) * safeSize;
        List<SysTenantEntity> entities = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .eq(platformLevel != null, SysTenantEntity::getPlatformLevel, Boolean.TRUE.equals(platformLevel) ? 1 : 0)
                .eq(tenantStatus != null, SysTenantEntity::getTenantStatus, tenantStatus)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysTenantEntity::getTenantId, keyword)
                        .or()
                        .like(SysTenantEntity::getTenantName, keyword))
                .orderByDesc(SysTenantEntity::getCreatedAt)
                .orderByDesc(SysTenantEntity::getId)
                .last("limit " + offset + "," + safeSize));
        Map<String, Map<String, String>> profiles = loadTenantProfileValues(entities.stream().map(SysTenantEntity::getTenantId).toList());
        Map<String, String> capabilityDocs = loadCapabilityDocs();
        List<CatalogService.TenantView> records = entities.stream()
                .map(entity -> toTenantView(entity, profiles.getOrDefault(entity.getTenantId(), Map.of()), capabilityDocs))
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    public PageResult<TenantChangeView> history(
            String tenantId,
            String changeType,
            String fieldKey,
            String operator,
            java.time.Instant occurredFrom,
            java.time.Instant occurredTo,
            int page,
            int size
    ) {
        requireDatabaseMode();
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        LambdaQueryWrapper<SysTenantChangeLogEntity> query = new LambdaQueryWrapper<SysTenantChangeLogEntity>()
                .eq(SysTenantChangeLogEntity::getTenantId, tenantId)
                .eq(StringUtils.hasText(changeType), SysTenantChangeLogEntity::getChangeType, changeType)
                .eq(StringUtils.hasText(fieldKey), SysTenantChangeLogEntity::getFieldKey, fieldKey)
                .like(StringUtils.hasText(operator), SysTenantChangeLogEntity::getOperator, operator)
                .ge(occurredFrom != null, SysTenantChangeLogEntity::getOccurredAt, occurredFrom == null ? null : java.time.LocalDateTime.ofInstant(occurredFrom, ZoneId.systemDefault()))
                .le(occurredTo != null, SysTenantChangeLogEntity::getOccurredAt, occurredTo == null ? null : java.time.LocalDateTime.ofInstant(occurredTo, ZoneId.systemDefault()))
                .orderByDesc(SysTenantChangeLogEntity::getOccurredAt)
                .orderByDesc(SysTenantChangeLogEntity::getId);
        long total = sysTenantChangeLogMapper.selectCount(query);
        if (total == 0) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }
        int offset = (safePage - 1) * safeSize;
        List<TenantChangeView> records = sysTenantChangeLogMapper.selectList(query.last("limit " + offset + "," + safeSize))
                .stream()
                .map(item -> new TenantChangeView(
                        item.getId(),
                        item.getTenantId(),
                        item.getChangeType(),
                        item.getFieldKey(),
                        item.getOldValue(),
                        item.getNewValue(),
                        item.getSummary(),
                        buildImpactSummary(item),
                        item.getOperator(),
                        item.getOccurredAt() == null ? null : item.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant()
                ))
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    private boolean existsTenant(String tenantId) {
        return sysTenantMapper.selectCount(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, tenantId)
                .eq(SysTenantEntity::getDeleted, 0)) > 0;
    }

    private SysTenantEntity getTenant(String tenantId) {
        SysTenantEntity entity = sysTenantMapper.selectOne(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, tenantId)
                .eq(SysTenantEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("租户不存在");
        }
        return entity;
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled()
                || sysTenantMapper == null
                || sysUserMapper == null
                || sysRoleMapper == null
                || sysDeptMapper == null
                || sysConfigMapper == null
                || sysTenantChangeLogMapper == null) {
            throw new BusinessException("当前为默认内存模式，暂未启用数据库写入能力");
        }
    }

    private void saveTenantProfile(
            String tenantId,
            String packageCode,
            String packageName,
            Integer userQuota,
            Integer storageQuotaGb,
            java.util.List<String> capabilityCodes,
            String lifecycleNote
    ) {
        upsertTenantConfig(tenantId, "tenant.package.code", "租户套餐编码", packageCode);
        upsertTenantConfig(tenantId, "tenant.package.name", "租户套餐名称", packageName);
        upsertTenantConfig(tenantId, "tenant.quota.users", "租户用户配额", userQuota == null ? null : String.valueOf(userQuota));
        upsertTenantConfig(tenantId, "tenant.quota.storage_gb", "租户存储配额(GB)", storageQuotaGb == null ? null : String.valueOf(storageQuotaGb));
        upsertTenantConfig(tenantId, "tenant.capability.codes", "租户能力编码集合",
                capabilityCodes == null || capabilityCodes.isEmpty() ? null : String.join(",", capabilityCodes));
        upsertTenantConfig(tenantId, "tenant.lifecycle.note", "租户运营备注", lifecycleNote);
    }

    private void upsertTenantConfig(String tenantId, String key, String name, String value) {
        if (sysConfigMapper == null) {
            return;
        }
        SysConfigEntity existing = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getTenantId, tenantId)
                .eq(SysConfigEntity::getConfigKey, key)
                .eq(SysConfigEntity::getDeleted, 0)
                .last("limit 1"));
        if (!org.springframework.util.StringUtils.hasText(value)) {
            if (existing != null) {
                sysConfigMapper.deleteById(existing.getId());
            }
            return;
        }
        if (existing == null) {
            existing = new SysConfigEntity();
            existing.setTenantId(tenantId);
            existing.setConfigKey(key);
            existing.setConfigName(name);
            existing.setConfigValue(value);
            sysConfigMapper.insert(existing);
            return;
        }
        existing.setConfigName(name);
        existing.setConfigValue(value);
        sysConfigMapper.updateById(existing);
    }

    private Map<String, Map<String, String>> loadTenantProfileValues(List<String> tenantIds) {
        if (tenantIds.isEmpty()) {
            return Map.of();
        }
        List<SysConfigEntity> configs = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigEntity>()
                .in(SysConfigEntity::getTenantId, tenantIds)
                .eq(SysConfigEntity::getDeleted, 0)
                .in(SysConfigEntity::getConfigKey,
                        "tenant.package.code",
                        "tenant.package.name",
                        "tenant.quota.users",
                        "tenant.quota.storage_gb",
                        "tenant.capability.codes",
                        "tenant.lifecycle.note"));
        Map<String, Map<String, String>> result = new java.util.LinkedHashMap<>();
        for (SysConfigEntity config : configs) {
            result.computeIfAbsent(config.getTenantId(), ignored -> new java.util.LinkedHashMap<>())
                    .put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    private CatalogService.TenantView toTenantView(SysTenantEntity tenant, Map<String, String> values, Map<String, String> capabilityDocs) {
        List<String> capabilityCodes = parseCodes(values.get("tenant.capability.codes"));
        return new CatalogService.TenantView(
                tenant.getTenantId(),
                tenant.getTenantName(),
                tenant.getPlatformLevel() != null && tenant.getPlatformLevel() == 1,
                tenant.getTenantStatus(),
                tenant.getExpireAt(),
                values.get("tenant.package.code"),
                values.get("tenant.package.name"),
                parseInteger(values.get("tenant.quota.users")),
                parseInteger(values.get("tenant.quota.storage_gb")),
                capabilityCodes,
                capabilityCodes.stream().collect(java.util.stream.Collectors.toMap(
                        java.util.function.Function.identity(),
                        code -> capabilityDocs.getOrDefault(code, defaultCapabilityDescription(code)),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                )),
                values.get("tenant.lifecycle.note")
        );
    }

    private Map<String, String> loadCapabilityDocs() {
        if (sysConfigMapper == null) {
            return Map.of();
        }
        String prefix = "tenant.capability.doc.";
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigEntity>()
                        .eq(SysConfigEntity::getTenantId, "platform")
                        .eq(SysConfigEntity::getDeleted, 0)
                        .likeRight(SysConfigEntity::getConfigKey, prefix))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getConfigKey().substring(prefix.length()),
                        SysConfigEntity::getConfigValue,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    private String defaultCapabilityDescription(String code) {
        return switch (code) {
            case "oauth" -> "统一认证、授权码登录与开放接入能力";
            case "user" -> "用户目录、启停、角色分配与组织可见范围管理";
            case "role" -> "角色模型、权限树与数据范围授权";
            case "dept" -> "组织树、负责人和部门层级治理";
            case "tenant" -> "租户套餐、能力配置与生命周期治理";
            case "system" -> "字典、参数、公告与分类配置管理";
            case "audit" -> "审计查询、导出与授权记录联动";
            case "notice" -> "公告发布与租户通知能力";
            default -> code + " 能力已启用，可在租户侧继续扩展说明。";
        };
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> parseCodes(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private void recordIfChanged(String tenantId, String changeType, String fieldKey, String oldValue, String newValue, String summary, String operator) {
        if (java.util.Objects.equals(trimToNull(oldValue), trimToNull(newValue))) {
            return;
        }
        recordTenantChange(tenantId, changeType, fieldKey, oldValue, newValue, summary, operator);
    }

    private void recordTenantChange(String tenantId, String changeType, String fieldKey, String oldValue, String newValue, String summary, String operator) {
        SysTenantChangeLogEntity entity = new SysTenantChangeLogEntity();
        entity.setTenantId(tenantId);
        entity.setChangeType(changeType);
        entity.setFieldKey(fieldKey);
        entity.setOldValue(trimToNull(oldValue));
        entity.setNewValue(trimToNull(newValue));
        entity.setSummary(summary);
        entity.setOperator(operator);
        sysTenantChangeLogMapper.insert(entity);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
        if ("tenantStatus".equals(fieldKey)) {
            return "租户状态变更会直接影响登录、访问和管理操作可用性。";
        }
        if ("expireAt".equals(fieldKey)) {
            return "到期时间变更会影响续费提醒、停用策略和运营排期。";
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
            @Schema(description = "变更时间") java.time.Instant occurredAt
    ) {
    }
}
