package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysCategoryRuleEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysDictEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysDictValueEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysCategoryRuleMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysDictMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysDictValueMapper;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.system.interfaces.DictCrudRequest;
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
public class DictApplicationService {

    private static final String SORT_ASC = "asc";
    private static final String SORT_DESC = "desc";
    private static final String DICT_CATEGORY_PREFIX = "system.category.dict.";
    private static final int DICT_TYPE_MAX_LENGTH = 64;
    private static final int DICT_CODE_MAX_LENGTH = 64;
    private static final int DICT_VALUE_MAX_LENGTH = 255;

    private final SysDictMapper sysDictMapper;
    private final SysDictValueMapper sysDictValueMapper;
    private final SysCategoryRuleMapper sysCategoryRuleMapper;
    private final DataScopeService dataScopeService;

    public DictApplicationService(
            SysDictMapper sysDictMapper,
            SysDictValueMapper sysDictValueMapper,
            SysCategoryRuleMapper sysCategoryRuleMapper,
            DataScopeService dataScopeService
    ) {
        this.sysDictMapper = sysDictMapper;
        this.sysDictValueMapper = sysDictValueMapper;
        this.sysCategoryRuleMapper = sysCategoryRuleMapper;
        this.dataScopeService = dataScopeService;
    }

    @Cacheable(value = CacheNames.SYSTEM_DICTS, key = "#root.target.generateCacheKey(new Object[]{#dictType, #category, #keyword, #page, #size, #sortBy, #sortDirection})")
    public PageResult<SystemViewModels.DictView> dicts(
            String dictType,
            String category,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        String tenantId = currentTenantId();
        Optional<Set<String>> visibleCreators = dataScopeService.visibleUsernames(tenantId);
        String normalizedDictType = blankToNull(dictType);
        String normalizedCategory = blankToNull(category);
        String normalizedKeyword = blankToNull(keyword);
        return pageQuery(
                buildDictQuery(tenantId, normalizedDictType, normalizedCategory, normalizedKeyword, visibleCreators),
                buildDictQuery(tenantId, normalizedDictType, normalizedCategory, normalizedKeyword, visibleCreators),
                page,
                size,
                sysDictMapper::selectCount,
                query -> sysDictMapper.selectList(query).stream().map(this::toDictView).toList(),
                resolveDictSort(sortBy),
                resolveDirection(sortDirection, SORT_ASC)
        );
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_DICTS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET}, allEntries = true)
    public SystemViewModels.DictView createDict(DictCrudRequest request) {
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        String dictType = normalizeCode(request.dictType(), "字典类型不能为空", DICT_TYPE_MAX_LENGTH, "字典类型长度不能超过64个字符");
        ensureDictTypeUnique(tenantId, dictType, null);

        SysDictEntity entity = new SysDictEntity();
        entity.setTenantId(tenantId);
        applyDictProfile(entity, request, dictType);
        sysDictMapper.insert(entity);
        return toDictView(entity);
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_DICTS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET}, allEntries = true)
    public SystemViewModels.DictView updateDict(Long id, DictCrudRequest request) {
        String tenantId = currentTenantId();
        SysDictEntity entity = getDict(id, tenantId);
        String dictType = normalizeCode(request.dictType(), "字典类型不能为空", DICT_TYPE_MAX_LENGTH, "字典类型长度不能超过64个字符");
        ensureDictTypeUnique(tenantId, dictType, id);
        String oldType = entity.getDictType();
        applyDictProfile(entity, request, dictType);
        sysDictMapper.updateById(entity);
        if (!oldType.equals(dictType)) {
            syncValueTypes(tenantId, id, dictType);
        }
        return toDictView(entity);
    }

    public SystemViewModels.DictDetailView detail(Long id) {
        String tenantId = currentTenantId();
        SysDictEntity entity = getDict(id, tenantId);
        return new SystemViewModels.DictDetailView(toDictView(entity), valueViews(listValues(tenantId, entity.getId(), false)));
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_DICTS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET}, allEntries = true)
    public void deleteDict(Long id) {
        String tenantId = currentTenantId();
        SysDictEntity entity = getDict(id, tenantId);
        for (SysDictValueEntity value : listValues(tenantId, entity.getId(), false)) {
            releaseDeletedValueIdentity(value);
            sysDictValueMapper.updateById(value);
            sysDictValueMapper.deleteById(value.getId());
        }
        releaseDeletedDictIdentity(entity);
        sysDictMapper.updateById(entity);
        sysDictMapper.deleteById(entity.getId());
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

    private SystemViewModels.DictView toDictView(SysDictEntity entity) {
        String tenantId = currentTenantId();
        return new SystemViewModels.DictView(
                entity.getId(),
                entity.getDictType(),
                deriveCategory(tenantId, entity.getDictType()),
                entity.getDictCode(),
                entity.getDictValue(),
                StringUtils.hasText(entity.getDescription()) ? entity.getDescription() : entity.getDictValue(),
                entity.getEnabled() == null || entity.getEnabled() == 1,
                entity.getRemarks(),
                countValues(tenantId, entity.getId()),
                TimeSupport.toEpochMilli(entity.getUpdatedAt()),
                entity.getCreatedBy()
        );
    }

    private LambdaQueryWrapper<SysDictEntity> buildDictQuery(
            String tenantId,
            String dictType,
            String category,
            String keyword,
            Optional<Set<String>> visibleCreators
    ) {
        LambdaQueryWrapper<SysDictEntity> query = new LambdaQueryWrapper<SysDictEntity>()
                .eq(SysDictEntity::getTenantId, tenantId)
                .eq(SysDictEntity::getDeleted, 0)
                .eq(StringUtils.hasText(dictType), SysDictEntity::getDictType, dictType)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysDictEntity::getDictType, keyword)
                        .or()
                        .like(SysDictEntity::getDictCode, keyword)
                        .or()
                        .like(SysDictEntity::getDictValue, keyword)
                        .or()
                        .like(SysDictEntity::getDescription, keyword)
                        .or()
                        .like(SysDictEntity::getRemarks, keyword));
        applyCategoryFilter(query, tenantId, category, SysDictEntity::getDictType);
        applyCreatorScope(query, visibleCreators, SysDictEntity::getCreatedBy);
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

    private SysDictEntity getDict(Long id, String tenantId) {
        SysDictEntity entity = sysDictMapper.selectOne(new LambdaQueryWrapper<SysDictEntity>()
                .eq(SysDictEntity::getId, id)
                .eq(SysDictEntity::getTenantId, tenantId)
                .eq(SysDictEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("字典项不存在");
        }
        if (!dataScopeService.canAccessCreatedBy(tenantId, entity.getCreatedBy())) {
            throw new BusinessException("无权访问该字典项");
        }
        return entity;
    }

    private List<SysDictValueEntity> listValues(String tenantId, Long dictId, boolean onlyEnabled) {
        return sysDictValueMapper.selectList(new LambdaQueryWrapper<SysDictValueEntity>()
                .eq(SysDictValueEntity::getTenantId, tenantId)
                .eq(SysDictValueEntity::getDictId, dictId)
                .eq(SysDictValueEntity::getDeleted, 0)
                .eq(onlyEnabled, SysDictValueEntity::getEnabled, 1)
                .orderByAsc(SysDictValueEntity::getSort)
                .orderByAsc(SysDictValueEntity::getId));
    }

    private Long countValues(String tenantId, Long dictId) {
        return sysDictValueMapper.selectCount(new LambdaQueryWrapper<SysDictValueEntity>()
                .eq(SysDictValueEntity::getTenantId, tenantId)
                .eq(SysDictValueEntity::getDictId, dictId)
                .eq(SysDictValueEntity::getDeleted, 0));
    }

    private List<SystemViewModels.DictValueView> valueViews(List<SysDictValueEntity> values) {
        return values.stream().map(this::toValueView).toList();
    }

    private SystemViewModels.DictValueView toValueView(SysDictValueEntity value) {
        return new SystemViewModels.DictValueView(
                value.getId(),
                value.getDictId(),
                value.getDictType(),
                value.getDictLabel(),
                value.getDictValue(),
                value.getSort(),
                value.getShowClass(),
                value.getEnabled() != null && value.getEnabled() == 1,
                value.getRemarks(),
                TimeSupport.toEpochMilli(value.getUpdatedAt())
        );
    }

    private void syncValueTypes(String tenantId, Long dictId, String dictType) {
        for (SysDictValueEntity value : listValues(tenantId, dictId, false)) {
            value.setDictType(dictType);
            sysDictValueMapper.updateById(value);
        }
    }

    private void applyDictProfile(SysDictEntity entity, DictCrudRequest request, String dictType) {
        String description = normalizeOptional(
                StringUtils.hasText(request.description()) ? request.description() : request.dictValue(),
                DICT_VALUE_MAX_LENGTH,
                "字典类型说明长度不能超过255个字符"
        );
        entity.setDictType(dictType);
        entity.setDictCode(dictType);
        entity.setDictValue(description == null ? dictType : description);
        entity.setDescription(description);
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        entity.setRemarks(normalizeOptional(request.remarks(), DICT_VALUE_MAX_LENGTH, "备注长度不能超过255个字符"));
    }

    private void ensureDictTypeUnique(String tenantId, String dictType, Long selfId) {
        Long count = sysDictMapper.selectCount(new LambdaQueryWrapper<SysDictEntity>()
                .eq(SysDictEntity::getTenantId, tenantId)
                .eq(SysDictEntity::getDeleted, 0)
                .eq(SysDictEntity::getDictType, dictType)
                .ne(selfId != null, SysDictEntity::getId, selfId == null ? -1L : selfId));
        if (count != null && count > 0) {
            throw new BusinessException("CONFLICT", "字典类型已存在");
        }
    }

    private void releaseDeletedDictIdentity(SysDictEntity entity) {
        entity.setDictType(tombstone(entity.getDictType(), entity.getId(), DICT_TYPE_MAX_LENGTH));
        entity.setDictCode(tombstone(entity.getDictCode(), entity.getId(), DICT_CODE_MAX_LENGTH));
        entity.setDictValue(tombstone(entity.getDictValue(), entity.getId(), DICT_VALUE_MAX_LENGTH));
    }

    private void releaseDeletedValueIdentity(SysDictValueEntity entity) {
        entity.setDictValue(tombstone(entity.getDictValue(), entity.getId(), DICT_VALUE_MAX_LENGTH));
    }

    private String normalizeCode(String value, String requiredMessage, int maxLength, String lengthMessage) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException(requiredMessage);
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

    private String tombstone(String value, Long id, int maxLength) {
        String suffix = "#deleted#" + id;
        String base = StringUtils.hasText(value) ? value.trim() : "deleted";
        int keepLength = Math.max(0, maxLength - suffix.length());
        if (base.length() > keepLength) {
            base = base.substring(0, keepLength);
        }
        return base + suffix;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String currentScopeCacheKey() {
        return dataScopeService.currentUser()
                .map(user -> user.username() + "|" + user.dataScopeType() + "|" + user.customDeptIds().stream().sorted().toList())
                .orElse("anonymous");
    }

    private SFunction<SysDictEntity, ?> resolveDictSort(String sortBy) {
        if ("dictType".equalsIgnoreCase(sortBy)) {
            return SysDictEntity::getDictType;
        }
        if ("dictCode".equalsIgnoreCase(sortBy)) {
            return SysDictEntity::getDictCode;
        }
        return SysDictEntity::getCreatedAt;
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
                        .eq(SysCategoryRuleEntity::getTargetType, "dict")
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