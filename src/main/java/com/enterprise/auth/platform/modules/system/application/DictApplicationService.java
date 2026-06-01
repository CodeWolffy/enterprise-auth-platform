package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysCategoryRuleEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysDictEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysCategoryRuleMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysDictMapper;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.system.interfaces.DictCrudRequest;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
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

    private final SysDictMapper sysDictMapper;
    private final SysCategoryRuleMapper sysCategoryRuleMapper;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;

    public DictApplicationService(
            SysDictMapper sysDictMapper,
            SysCategoryRuleMapper sysCategoryRuleMapper,
            AuditService auditService,
            DataScopeService dataScopeService
    ) {
        this.sysDictMapper = sysDictMapper;
        this.sysCategoryRuleMapper = sysCategoryRuleMapper;
        this.auditService = auditService;
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
        return pageQuery(
                buildDictQuery(tenantId, dictType, category, keyword, visibleCreators),
                buildDictQuery(tenantId, dictType, category, keyword, visibleCreators),
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
        SysDictEntity entity = new SysDictEntity();
        entity.setTenantId(tenantId);
        entity.setDictType(request.dictType());
        entity.setDictCode(request.dictCode());
        entity.setDictValue(request.dictValue());
        sysDictMapper.insert(entity);
        auditService.record("DICT_CREATED", operator, tenantId, Map.of("dictId", entity.getId()));
        return toDictView(entity);
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_DICTS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET}, allEntries = true)
    public SystemViewModels.DictView updateDict(Long id, DictCrudRequest request) {
        String tenantId = currentTenantId();
        SysDictEntity entity = getDict(id, tenantId);
        entity.setDictType(request.dictType());
        entity.setDictCode(request.dictCode());
        entity.setDictValue(request.dictValue());
        sysDictMapper.updateById(entity);
        auditService.record("DICT_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("dictId", id));
        return toDictView(entity);
    }

    @Transactional
    @CacheEvict(value = {CacheNames.SYSTEM_DICTS, CacheNames.SYSTEM_CATEGORIES_ALL, CacheNames.SYSTEM_CATEGORIES_TARGET}, allEntries = true)
    public void deleteDict(Long id) {
        String tenantId = currentTenantId();
        SysDictEntity entity = getDict(id, tenantId);
        sysDictMapper.deleteById(entity.getId());
        auditService.record("DICT_DELETED", SecuritySupport.currentOperator(), tenantId, Map.of("dictId", id));
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
        return new SystemViewModels.DictView(
                entity.getId(),
                entity.getDictType(),
                deriveCategory(currentTenantId(), entity.getDictType()),
                entity.getDictCode(),
                entity.getDictValue(),
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
                        .like(SysDictEntity::getDictCode, keyword)
                        .or()
                        .like(SysDictEntity::getDictValue, keyword));
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