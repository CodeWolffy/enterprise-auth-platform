package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysCategoryRuleEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysConfigEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysCategoryRuleMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysConfigMapper;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.system.interfaces.ConfigCrudRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConfigApplicationService {

    private static final String SORT_ASC = "asc";
    private static final String SORT_DESC = "desc";
    private static final String CONFIG_CATEGORY_PREFIX = "system.category.config.";
    private static final int CONFIG_KEY_MAX_LENGTH = 128;
    private static final int CONFIG_NAME_MAX_LENGTH = 128;
    private static final int CONFIG_VALUE_MAX_LENGTH = 500;
    private static final int CONFIG_TYPE_MAX_LENGTH = 32;
    private static final int CONFIG_REMARK_MAX_LENGTH = 255;
    private static final String CONFIG_TYPE_BUSINESS = "business";
    private static final String CONFIG_TYPE_SYSTEM = "system";

    private final SysConfigMapper sysConfigMapper;
    private final SysCategoryRuleMapper sysCategoryRuleMapper;
    private final DataScopeService dataScopeService;

    public ConfigApplicationService(
            SysConfigMapper sysConfigMapper,
            SysCategoryRuleMapper sysCategoryRuleMapper,
            DataScopeService dataScopeService
    ) {
        this.sysConfigMapper = sysConfigMapper;
        this.sysCategoryRuleMapper = sysCategoryRuleMapper;
        this.dataScopeService = dataScopeService;
    }

    public Optional<String> getConfigValue(String tenantId, String configKey) {
        SysConfigEntity entity = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getTenantId, tenantId)
                .eq(SysConfigEntity::getConfigKey, configKey)
                .eq(SysConfigEntity::getEnabled, true)
                .eq(SysConfigEntity::getDeleted, 0)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.ofNullable(entity.getConfigValue());
    }

    @Cacheable(value = CacheNames.SYSTEM_CONFIGS, key = "#root.target.generateCacheKey(new Object[]{#category, #keyword, #page, #size, #sortBy, #sortDirection})")
    public PageResult<SystemViewModels.ConfigView> configs(
            String category,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        String tenantId = currentTenantId();
        boolean globalScope = TenantContext.isGlobalScope();
        Optional<Set<String>> visibleCreators = globalScope ? Optional.empty() : dataScopeService.visibleUsernames(tenantId);
        return pageQuery(
                buildConfigQuery(tenantId, globalScope, category, keyword, visibleCreators),
                buildConfigQuery(tenantId, globalScope, category, keyword, visibleCreators),
                page,
                size,
                sysConfigMapper::selectCount,
                query -> sysConfigMapper.selectList(query).stream().map(this::toConfigView).toList(),
                resolveConfigSort(sortBy),
                resolveDirection(sortDirection, SORT_ASC)
        );
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_CONFIGS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET, CacheNames.REGISTRATION_POLICY}, allEntries = true)
    public SystemViewModels.ConfigView createConfig(ConfigCrudRequest request) {
        String tenantId = currentTenantId();
        String configKey = normalizeCode(request.configKey(), "参数键不能为空", CONFIG_KEY_MAX_LENGTH, "参数键长度不能超过128个字符");
        ensureConfigKeyUnique(tenantId, configKey, null);
        SysConfigEntity entity = new SysConfigEntity();
        entity.setTenantId(tenantId);
        applyConfigProfile(entity, request, configKey);
        sysConfigMapper.insert(entity);
        return toConfigView(entity);
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_CONFIGS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET, CacheNames.REGISTRATION_POLICY}, allEntries = true)
    public SystemViewModels.ConfigView updateConfig(Long id, ConfigCrudRequest request) {
        String tenantId = currentTenantId();
        SysConfigEntity entity = getConfig(id, tenantId);
        String configKey = normalizeCode(request.configKey(), "参数键不能为空", CONFIG_KEY_MAX_LENGTH, "参数键长度不能超过128个字符");
        ensureConfigKeyUnique(tenantId, configKey, id);
        applyConfigProfile(entity, request, configKey);
        sysConfigMapper.updateById(entity);
        return toConfigView(entity);
    }

    public SystemViewModels.ConfigDetailView detail(Long id) {
        String tenantId = currentTenantId();
        SysConfigEntity entity = getConfig(id, tenantId);
        return new SystemViewModels.ConfigDetailView(
                toConfigView(entity),
                Map.of(
                        "tenantId", entity.getTenantId(),
                        "category", deriveCategory(tenantId, entity.getConfigKey()),
                        "keyPrefix", keyPrefix(entity.getConfigKey())
                )
        );
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_CONFIGS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET, CacheNames.REGISTRATION_POLICY}, allEntries = true)
    public void deleteConfig(Long id) {
        String tenantId = currentTenantId();
        SysConfigEntity entity = getConfig(id, tenantId);
        if (Boolean.TRUE.equals(entity.getBuiltin())) {
            throw new BusinessException("内置参数不允许删除");
        }
        releaseDeletedConfigIdentity(entity);
        sysConfigMapper.updateById(entity);
        sysConfigMapper.deleteById(entity.getId());
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_CONFIGS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET, CacheNames.REGISTRATION_POLICY}, allEntries = true)
    public void deleteConfigs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            deleteConfig(id);
        }
    }

    @CacheEvict(value = {CacheNames.SYSTEM_CONFIGS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET, CacheNames.REGISTRATION_POLICY}, allEntries = true)
    public String refreshCache() {
        return "OK";
    }

    public String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    public String generateCacheKey(Object... params) {
        StringBuilder key = new StringBuilder(currentTenantId())
                .append(':')
                .append(currentScopeCacheKey());
        for (Object param : params) {
            key.append(':').append(param == null ? "" : param);
        }
        return key.toString();
    }

    private SystemViewModels.ConfigView toConfigView(SysConfigEntity entity) {
        return new SystemViewModels.ConfigView(
                entity.getId(),
                entity.getConfigKey(),
                deriveCategory(currentTenantId(), entity.getConfigKey()),
                entity.getConfigName(),
                entity.getConfigValue(),
                normalizeConfigType(entity.getConfigType()),
                Boolean.TRUE.equals(entity.getEnabled()),
                Boolean.TRUE.equals(entity.getBuiltin()),
                entity.getRemark(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy()
        );
    }

    private LambdaQueryWrapper<SysConfigEntity> buildConfigQuery(
            String tenantId,
            boolean globalScope,
            String category,
            String keyword,
            Optional<Set<String>> visibleCreators
    ) {
        LambdaQueryWrapper<SysConfigEntity> query = new LambdaQueryWrapper<SysConfigEntity>()
                .eq(!globalScope, SysConfigEntity::getTenantId, tenantId)
                .eq(SysConfigEntity::getDeleted, 0)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysConfigEntity::getConfigKey, keyword)
                        .or()
                        .like(SysConfigEntity::getConfigName, keyword)
                        .or()
                        .like(SysConfigEntity::getConfigValue, keyword)
                        .or()
                        .like(SysConfigEntity::getRemark, keyword));
        applyCategoryFilter(query, tenantId, category, SysConfigEntity::getConfigKey);
        applyCreatorScope(query, visibleCreators, SysConfigEntity::getCreatedBy);
        return query;
    }

    private <E, V> PageResult<V> pageQuery(
            LambdaQueryWrapper<E> countQuery,
            LambdaQueryWrapper<E> listQuery,
            int page,
            int size,
            Function<LambdaQueryWrapper<E>, Long> counter,
            Function<LambdaQueryWrapper<E>, List<V>> recordsLoader,
            SFunction<E, ?> orderField,
            String direction
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        long total = counter.apply(countQuery);
        if (total == 0) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }
        int offset = (safePage - 1) * safeSize;
        if (SORT_ASC.equals(direction)) {
            listQuery.orderByAsc(orderField);
        } else {
            listQuery.orderByDesc(orderField);
        }
        listQuery.last("limit " + offset + "," + safeSize);
        return PageResult.of(total, safePage, safeSize, recordsLoader.apply(listQuery));
    }

    private SysConfigEntity getConfig(Long id, String tenantId) {
        SysConfigEntity entity = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getId, id)
                .eq(SysConfigEntity::getTenantId, tenantId)
                .eq(SysConfigEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("参数不存在");
        }
        if (!dataScopeService.canAccessCreatedBy(tenantId, entity.getCreatedBy())) {
            throw new BusinessException("无权访问该参数");
        }
        return entity;
    }

    private void applyConfigProfile(SysConfigEntity entity, ConfigCrudRequest request, String configKey) {
        entity.setConfigKey(configKey);
        entity.setConfigName(normalizeOptional(request.configName(), CONFIG_NAME_MAX_LENGTH, "参数名称长度不能超过128个字符"));
        entity.setConfigValue(normalizeCode(request.configValue(), "参数值不能为空", CONFIG_VALUE_MAX_LENGTH, "参数值长度不能超过500个字符"));
        entity.setConfigType(normalizeConfigType(request.configType()));
        entity.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
        entity.setBuiltin(Boolean.TRUE.equals(request.builtin()));
        entity.setRemark(normalizeOptional(request.remark(), CONFIG_REMARK_MAX_LENGTH, "备注长度不能超过255个字符"));
    }

    private String normalizeConfigType(String value) {
        String normalized = normalizeOptional(value, CONFIG_TYPE_MAX_LENGTH, "参数类型长度不能超过32个字符");
        if (normalized == null) {
            return CONFIG_TYPE_BUSINESS;
        }
        String lowerCase = normalized.toLowerCase(java.util.Locale.ROOT);
        if (CONFIG_TYPE_BUSINESS.equals(lowerCase) || CONFIG_TYPE_SYSTEM.equals(lowerCase)) {
            return lowerCase;
        }
        throw new BusinessException("VALIDATION_ERROR", "参数类型只能是 business 或 system");
    }

    private void ensureConfigKeyUnique(String tenantId, String configKey, Long selfId) {
        Long count = sysConfigMapper.selectCount(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getTenantId, tenantId)
                .eq(SysConfigEntity::getDeleted, 0)
                .eq(SysConfigEntity::getConfigKey, configKey)
                .ne(selfId != null, SysConfigEntity::getId, selfId == null ? -1L : selfId));
        if (count != null && count > 0) {
            throw new BusinessException("CONFLICT", "参数键已存在");
        }
    }

    private void releaseDeletedConfigIdentity(SysConfigEntity entity) {
        entity.setConfigKey(tombstone(entity.getConfigKey(), entity.getId(), CONFIG_KEY_MAX_LENGTH));
    }

    private String normalizeCode(String value, String requiredMessage, int maxLength, String lengthMessage) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException("VALIDATION_ERROR", requiredMessage);
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException("VALIDATION_ERROR", lengthMessage);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String lengthMessage) {
        String normalized = blankToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new BusinessException("VALIDATION_ERROR", lengthMessage);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String tombstone(String value, Long id, int maxLength) {
        String suffix = "#deleted#" + id;
        String base = StringUtils.hasText(value) ? value.trim() : "deleted";
        int keepLength = Math.max(0, maxLength - suffix.length());
        if (base.length() > keepLength) {
            base = base.substring(0, keepLength);
        }
        return base + suffix;
    }

    private String keyPrefix(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return "default";
        }
        String normalized = configKey.trim();
        int dotIndex = normalized.indexOf('.');
        int colonIndex = normalized.indexOf(':');
        int underscoreIndex = normalized.indexOf('_');
        int splitIndex = StreamUtil.minPositive(dotIndex, colonIndex, underscoreIndex);
        return splitIndex > 0 ? normalized.substring(0, splitIndex) : normalized;
    }

    private String currentScopeCacheKey() {
        return dataScopeService.currentUser()
                .map(user -> user.username() + "|" + user.dataScopeType() + "|" + user.customDeptIds().stream().sorted().toList())
                .orElse("anonymous");
    }

    private SFunction<SysConfigEntity, ?> resolveConfigSort(String sortBy) {
        if ("configKey".equalsIgnoreCase(sortBy)) {
            return SysConfigEntity::getConfigKey;
        }
        if ("configName".equalsIgnoreCase(sortBy)) {
            return SysConfigEntity::getConfigName;
        }
        return SysConfigEntity::getCreatedAt;
    }

    private String resolveDirection(String sortDirection, String defaultValue) {
        return SORT_ASC.equalsIgnoreCase(sortDirection)
                ? SORT_ASC
                : SORT_DESC.equalsIgnoreCase(sortDirection) ? SORT_DESC : defaultValue;
    }

    private String deriveCategory(String tenantId, String rawKey) {
        for (SystemViewModels.CategoryOption option : loadCategoryOptions(tenantId)) {
            if (option.matches(rawKey)) {
                return option.code();
            }
        }
        if (!StringUtils.hasText(rawKey)) {
            return "default";
        }
        String normalized = rawKey.trim();
        int dotIndex = normalized.indexOf('.');
        int colonIndex = normalized.indexOf(':');
        int underscoreIndex = normalized.indexOf('_');
        int splitIndex = StreamUtil.minPositive(dotIndex, colonIndex, underscoreIndex);
        return splitIndex > 0 ? normalized.substring(0, splitIndex) : normalized;
    }

    private <E> void applyCreatorScope(LambdaQueryWrapper<E> query, Optional<Set<String>> visibleCreators, SFunction<E, ?> field) {
        visibleCreators.ifPresent(usernames -> {
            if (usernames.isEmpty()) {
                query.apply("1 = 0");
                return;
            }
            query.in(field, usernames);
        });
    }

    private <E> void applyCategoryFilter(LambdaQueryWrapper<E> query, String tenantId, String category, SFunction<E, ?> field) {
        if (!StringUtils.hasText(category)) {
            return;
        }
        SystemViewModels.CategoryOption configured = loadCategoryOptions(tenantId).stream()
                .filter(option -> option.code().equals(category))
                .findFirst()
                .orElse(null);
        if (configured == null) {
            query.and(wrapper -> wrapper.eq(field, category)
                    .or()
                    .likeRight(field, category + ".")
                    .or()
                    .likeRight(field, category + ":")
                    .or()
                    .likeRight(field, category + "_"));
            return;
        }
        query.and(wrapper -> {
            boolean first = true;
            for (String matcher : configured.matchers()) {
                if (!StringUtils.hasText(matcher)) {
                    continue;
                }
                if (!first) {
                    wrapper.or();
                }
                if (matcher.endsWith("*")) {
                    wrapper.likeRight(field, matcher.substring(0, matcher.length() - 1));
                } else {
                    wrapper.eq(field, matcher);
                }
                first = false;
            }
        });
    }

    private List<SystemViewModels.CategoryOption> loadCategoryOptions(String tenantId) {
        return sysCategoryRuleMapper.selectList(new LambdaQueryWrapper<SysCategoryRuleEntity>()
                        .eq(SysCategoryRuleEntity::getTenantId, tenantId)
                        .eq(SysCategoryRuleEntity::getTargetType, "config")
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

    private List<String> splitMatchers(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static final class StreamUtil {
        private StreamUtil() {
        }

        private static int minPositive(int... values) {
            int min = Integer.MAX_VALUE;
            for (int value : values) {
                if (value > 0 && value < min) {
                    min = value;
                }
            }
            return min == Integer.MAX_VALUE ? -1 : min;
        }
    }
}
