package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.context.TimeZoneContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysCategoryRuleEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysConfigEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysDictEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysCategoryRuleMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysConfigMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysDictMapper;
import com.enterprise.auth.platform.modules.system.interfaces.CategoryConfigRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CategoryRuleApplicationService {

    private static final String DICT_CATEGORY_PREFIX = "system.category.dict.";
    private static final String CONFIG_CATEGORY_PREFIX = "system.category.config.";

    private final SysCategoryRuleMapper sysCategoryRuleMapper;
    private final SysDictMapper sysDictMapper;
    private final SysConfigMapper sysConfigMapper;
    private final DataScopeService dataScopeService;

    public CategoryRuleApplicationService(
            SysCategoryRuleMapper sysCategoryRuleMapper,
            SysDictMapper sysDictMapper,
            SysConfigMapper sysConfigMapper,
            DataScopeService dataScopeService
    ) {
        this.sysCategoryRuleMapper = sysCategoryRuleMapper;
        this.sysDictMapper = sysDictMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.dataScopeService = dataScopeService;
    }

    @Cacheable(value = CacheNames.SYSTEM_CATEGORIES_ALL,
            key = "T(com.enterprise.auth.platform.common.context.TenantContextSupport).currentTenantIdOrPlatform()")
    public Map<String, List<SystemViewModels.CategoryOption>> categories() {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        return Map.of(
                "dict", loadCategoryOptions(tenantId, DICT_CATEGORY_PREFIX),
                "config", loadCategoryOptions(tenantId, CONFIG_CATEGORY_PREFIX)
        );
    }

    @Cacheable(value = CacheNames.SYSTEM_CATEGORIES_TARGET, key = "#root.target.generateCacheKey(new Object[]{#targetType})")
    public List<SystemViewModels.CategoryOption> categoryOptions(String targetType) {
        return loadCategoryOptions(TenantContextSupport.currentTenantIdOrPlatform(), prefixForTargetType(targetType));
    }

    public SystemViewModels.CategoryAnalysis analyzeCategoryOption(String targetType, String code) {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        SysCategoryRuleEntity entity = getCategoryConfig(tenantId, targetType, code);
        List<String> matchers = splitMatchers(entity.getMatchers());
        int referenceCount = "dict".equalsIgnoreCase(targetType)
                ? countMatchingDicts(tenantId, matchers)
                : countMatchingConfigs(tenantId, matchers);
        List<String> sampleReferences = "dict".equalsIgnoreCase(targetType)
                ? sampleMatchingDicts(tenantId, matchers)
                : sampleMatchingConfigs(tenantId, matchers);
        List<SystemViewModels.CategoryAuditView> recentAudits = loadCategoryAudits(tenantId, targetType, code);
        List<SystemViewModels.CategoryTrendPoint> trend = buildCategoryTrend(recentAudits);
        return new SystemViewModels.CategoryAnalysis(
                code,
                entity.getCategoryName(),
                targetType.toLowerCase(),
                matchers,
                referenceCount,
                sampleReferences,
                recentAudits,
                trend
        );
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET, CacheNames.SYSTEM_DICTS, CacheNames.SYSTEM_CONFIGS}, allEntries = true)
    public SystemViewModels.CategoryOption createCategoryOption(String targetType, CategoryConfigRequest request) {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        if (sysCategoryRuleMapper.selectCount(new LambdaQueryWrapper<SysCategoryRuleEntity>()
                .eq(SysCategoryRuleEntity::getTenantId, tenantId)
                .eq(SysCategoryRuleEntity::getTargetType, targetType.toLowerCase())
                .eq(SysCategoryRuleEntity::getCategoryCode, request.code())
                .eq(SysCategoryRuleEntity::getDeleted, 0)) > 0) {
            throw new BusinessException("分类编码已存在");
        }
        SysCategoryRuleEntity entity = new SysCategoryRuleEntity();
        entity.setTenantId(tenantId);
        entity.setTargetType(targetType.toLowerCase());
        entity.setCategoryCode(request.code());
        entity.setCategoryName(request.name());
        entity.setMatchers(normalizeMatchers(request.matchers()));
        sysCategoryRuleMapper.insert(entity);
        return new SystemViewModels.CategoryOption(request.code(), request.name(), splitMatchers(entity.getMatchers()));
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET, CacheNames.SYSTEM_DICTS, CacheNames.SYSTEM_CONFIGS}, allEntries = true)
    public SystemViewModels.CategoryOption updateCategoryOption(String targetType, String code, CategoryConfigRequest request) {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        SysCategoryRuleEntity entity = getCategoryConfig(tenantId, targetType, code);
        entity.setCategoryName(request.name());
        entity.setMatchers(normalizeMatchers(request.matchers()));
        sysCategoryRuleMapper.updateById(entity);
        return new SystemViewModels.CategoryOption(code, request.name(), splitMatchers(entity.getMatchers()));
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET, CacheNames.SYSTEM_DICTS, CacheNames.SYSTEM_CONFIGS}, allEntries = true)
    public void deleteCategoryOption(String targetType, String code) {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        SysCategoryRuleEntity entity = getCategoryConfig(tenantId, targetType, code);
        sysCategoryRuleMapper.deleteById(entity.getId());
    }

    public String generateCacheKey(Object... params) {
        StringBuilder key = new StringBuilder(TenantContextSupport.currentTenantIdOrPlatform());
        for (Object param : params) {
            key.append(':').append(param == null ? "" : param);
        }
        return key.toString();
    }

    private int countMatchingDicts(String tenantId, List<String> matchers) {
        return sysDictMapper.selectList(new LambdaQueryWrapper<SysDictEntity>()
                        .eq(SysDictEntity::getTenantId, tenantId)
                        .eq(SysDictEntity::getDeleted, 0))
                .stream()
                .map(SysDictEntity::getDictType)
                .filter(raw -> matchesAny(matchers, raw))
                .toList()
                .size();
    }

    private int countMatchingConfigs(String tenantId, List<String> matchers) {
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigEntity>()
                        .eq(SysConfigEntity::getTenantId, tenantId)
                        .eq(SysConfigEntity::getDeleted, 0))
                .stream()
                .map(SysConfigEntity::getConfigKey)
                .filter(raw -> matchesAny(matchers, raw))
                .toList()
                .size();
    }

    private List<String> sampleMatchingDicts(String tenantId, List<String> matchers) {
        return sysDictMapper.selectList(new LambdaQueryWrapper<SysDictEntity>()
                        .eq(SysDictEntity::getTenantId, tenantId)
                        .eq(SysDictEntity::getDeleted, 0)
                        .orderByAsc(SysDictEntity::getDictType))
                .stream()
                .filter(item -> matchesAny(matchers, item.getDictType()))
                .map(SysDictEntity::getDictType)
                .distinct()
                .limit(5)
                .toList();
    }

    private List<String> sampleMatchingConfigs(String tenantId, List<String> matchers) {
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigEntity>()
                        .eq(SysConfigEntity::getTenantId, tenantId)
                        .eq(SysConfigEntity::getDeleted, 0)
                        .orderByAsc(SysConfigEntity::getConfigKey))
                .stream()
                .filter(item -> matchesAny(matchers, item.getConfigKey()))
                .map(SysConfigEntity::getConfigKey)
                .distinct()
                .limit(5)
                .toList();
    }

    private boolean matchesAny(List<String> matchers, String rawKey) {
        return matchers.stream().anyMatch(matcher -> {
            if (!StringUtils.hasText(matcher) || !StringUtils.hasText(rawKey)) {
                return false;
            }
            if (matcher.endsWith("*")) {
                return rawKey.startsWith(matcher.substring(0, matcher.length() - 1));
            }
            return rawKey.equals(matcher);
        });
    }

    private List<SystemViewModels.CategoryAuditView> loadCategoryAudits(String tenantId, String targetType, String code) {
        return List.of();
    }

    private List<SystemViewModels.CategoryTrendPoint> buildCategoryTrend(List<SystemViewModels.CategoryAuditView> audits) {
        java.time.ZoneId zone = TimeZoneContext.getZone();
        java.time.LocalDate today = TimeSupport.today(zone);
        Map<java.time.LocalDate, Long> counts = audits.stream()
                .filter(item -> item.occurredAt() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.occurredAt().atZone(zone).toLocalDate(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        List<SystemViewModels.CategoryTrendPoint> trend = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate day = today.minusDays(i);
            trend.add(new SystemViewModels.CategoryTrendPoint(day.toString(), counts.getOrDefault(day, 0L).intValue()));
        }
        return trend;
    }

    private List<SystemViewModels.CategoryOption> loadCategoryOptions(String tenantId, String prefix) {
        return sysCategoryRuleMapper.selectList(new LambdaQueryWrapper<SysCategoryRuleEntity>()
                        .eq(SysCategoryRuleEntity::getTenantId, tenantId)
                        .eq(SysCategoryRuleEntity::getTargetType, targetTypeFromPrefix(prefix))
                        .eq(SysCategoryRuleEntity::getDeleted, 0)
                        .orderByAsc(SysCategoryRuleEntity::getCategoryCode))
                .stream()
                .map(config -> new SystemViewModels.CategoryOption(
                        config.getCategoryCode(),
                        StringUtils.hasText(config.getCategoryName()) ? config.getCategoryName() : config.getCategoryCode(),
                        splitMatchers(config.getMatchers())
                ))
                .toList();
    }

    private SysCategoryRuleEntity getCategoryConfig(String tenantId, String targetType, String code) {
        SysCategoryRuleEntity entity = sysCategoryRuleMapper.selectOne(new LambdaQueryWrapper<SysCategoryRuleEntity>()
                .eq(SysCategoryRuleEntity::getTenantId, tenantId)
                .eq(SysCategoryRuleEntity::getTargetType, targetType.toLowerCase())
                .eq(SysCategoryRuleEntity::getCategoryCode, code)
                .eq(SysCategoryRuleEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("分类配置不存在");
        }
        if (!dataScopeService.canAccessCreatedBy(tenantId, entity.getCreatedBy())) {
            throw new BusinessException("无权访问该分类配置");
        }
        return entity;
    }

    private String prefixForTargetType(String targetType) {
        if ("dict".equalsIgnoreCase(targetType)) {
            return DICT_CATEGORY_PREFIX;
        }
        if ("config".equalsIgnoreCase(targetType)) {
            return CONFIG_CATEGORY_PREFIX;
        }
        throw new BusinessException("仅支持 dict 或 config 分类配置");
    }

    private String targetTypeFromPrefix(String prefix) {
        return DICT_CATEGORY_PREFIX.equals(prefix) ? "dict" : "config";
    }

    private String normalizeMatchers(List<String> matchers) {
        if (matchers == null || matchers.isEmpty()) {
            throw new BusinessException("匹配规则不能为空");
        }
        String normalized = matchers.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("匹配规则不能为空");
        }
        return normalized;
    }

    private List<String> splitMatchers(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
