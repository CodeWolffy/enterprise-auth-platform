package com.enterprise.auth.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysCategoryRuleEntity;
import com.enterprise.auth.platform.persistence.entity.SysConfigEntity;
import com.enterprise.auth.platform.persistence.entity.SysDictEntity;
import com.enterprise.auth.platform.persistence.entity.SysNoticeEntity;
import com.enterprise.auth.platform.persistence.entity.SysAuditLogEntity;
import com.enterprise.auth.platform.persistence.mapper.SysAuditLogMapper;
import com.enterprise.auth.platform.persistence.mapper.SysCategoryRuleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysConfigMapper;
import com.enterprise.auth.platform.persistence.mapper.SysDictMapper;
import com.enterprise.auth.platform.persistence.mapper.SysNoticeMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.system.dto.ConfigCrudRequest;
import com.enterprise.auth.platform.system.dto.CategoryConfigRequest;
import com.enterprise.auth.platform.system.dto.DictCrudRequest;
import com.enterprise.auth.platform.system.dto.NoticeCrudRequest;
import com.enterprise.auth.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.io.Serializable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemManagementService {

    private static final String SORT_ASC = "asc";
    private static final String SORT_DESC = "desc";
    private static final String DICT_CATEGORY_PREFIX = "system.category.dict.";
    private static final String CONFIG_CATEGORY_PREFIX = "system.category.config.";

    private final PersistenceProperties persistenceProperties;
    private final SysDictMapper sysDictMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysNoticeMapper sysNoticeMapper;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final SysCategoryRuleMapper sysCategoryRuleMapper;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;

    public SystemManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysDictMapper sysDictMapper,
            @Nullable SysConfigMapper sysConfigMapper,
            @Nullable SysNoticeMapper sysNoticeMapper,
            @Nullable SysAuditLogMapper sysAuditLogMapper,
            @Nullable SysCategoryRuleMapper sysCategoryRuleMapper,
            AuditService auditService,
            DataScopeService dataScopeService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysDictMapper = sysDictMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.sysNoticeMapper = sysNoticeMapper;
        this.sysAuditLogMapper = sysAuditLogMapper;
        this.sysCategoryRuleMapper = sysCategoryRuleMapper;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
    }

    @Cacheable(value = "system:dicts", key = "#root.target.generateCacheKey(new Object[]{#dictType, #category, #keyword, #page, #size, #sortBy, #sortDirection})")
    public PageResult<DictView> dicts(String dictType, String category, String keyword, int page, int size, String sortBy, String sortDirection) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        Optional<Set<String>> visibleCreators = dataScopeService.visibleUsernames(tenantId);
        return pageQuery(
                buildDictQuery(tenantId, dictType, category, keyword, visibleCreators),
                buildDictQuery(tenantId, dictType, category, keyword, visibleCreators),
                page,
                size,
                query -> sysDictMapper.selectCount(query),
                query -> sysDictMapper.selectList(query).stream().map(this::toDictView).toList(),
                resolveDictSort(sortBy),
                resolveDirection(sortDirection, SORT_ASC)
        );
    }

    @Transactional
    @CacheEvict(value = {"system:dicts", "system:categories:all", "system:categories:target"}, allEntries = true)
    public DictView createDict(DictCrudRequest request) {
        requireDatabaseMode();
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
    @CacheEvict(value = {"system:dicts", "system:categories:all", "system:categories:target"}, allEntries = true)
    public DictView updateDict(Long id, DictCrudRequest request) {
        requireDatabaseMode();
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
    @CacheEvict(value = {"system:dicts", "system:categories:all", "system:categories:target"}, allEntries = true)
    public void deleteDict(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysDictEntity entity = getDict(id, tenantId);
        sysDictMapper.deleteById(entity.getId());
        auditService.record("DICT_DELETED", SecuritySupport.currentOperator(), tenantId, Map.of("dictId", id));
    }

    @Cacheable(value = "system:configs", key = "#root.target.generateCacheKey(new Object[]{#category, #keyword, #page, #size, #sortBy, #sortDirection})")
    public PageResult<ConfigView> configs(String category, String keyword, int page, int size, String sortBy, String sortDirection) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        Optional<Set<String>> visibleCreators = dataScopeService.visibleUsernames(tenantId);
        return pageQuery(
                buildConfigQuery(tenantId, category, keyword, visibleCreators),
                buildConfigQuery(tenantId, category, keyword, visibleCreators),
                page,
                size,
                query -> sysConfigMapper.selectCount(query),
                query -> sysConfigMapper.selectList(query).stream().map(this::toConfigView).toList(),
                resolveConfigSort(sortBy),
                resolveDirection(sortDirection, SORT_ASC)
        );
    }

    @Transactional
    @CacheEvict(value = {"system:configs", "system:categories:all", "system:categories:target"}, allEntries = true)
    public ConfigView createConfig(ConfigCrudRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysConfigEntity entity = new SysConfigEntity();
        entity.setTenantId(tenantId);
        entity.setConfigKey(request.configKey());
        entity.setConfigName(request.configName());
        entity.setConfigValue(request.configValue());
        sysConfigMapper.insert(entity);
        auditService.record("CONFIG_CREATED", operator, tenantId, Map.of("configId", entity.getId()));
        return toConfigView(entity);
    }

    @Transactional
    @CacheEvict(value = {"system:configs", "system:categories:all", "system:categories:target"}, allEntries = true)
    public ConfigView updateConfig(Long id, ConfigCrudRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysConfigEntity entity = getConfig(id, tenantId);
        entity.setConfigKey(request.configKey());
        entity.setConfigName(request.configName());
        entity.setConfigValue(request.configValue());
        sysConfigMapper.updateById(entity);
        auditService.record("CONFIG_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("configId", id));
        return toConfigView(entity);
    }

    @Transactional
    @CacheEvict(value = {"system:configs", "system:categories:all", "system:categories:target"}, allEntries = true)
    public void deleteConfig(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysConfigEntity entity = getConfig(id, tenantId);
        sysConfigMapper.deleteById(entity.getId());
        auditService.record("CONFIG_DELETED", SecuritySupport.currentOperator(), tenantId, Map.of("configId", id));
    }

    public PageResult<NoticeView> notices(Boolean published, String workflowStatus, String keyword, int page, int size, String sortBy, String sortDirection) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        Optional<Set<String>> visibleCreators = dataScopeService.visibleUsernames(tenantId);
        return pageQuery(
                buildNoticeQuery(tenantId, published, workflowStatus, keyword, visibleCreators),
                buildNoticeQuery(tenantId, published, workflowStatus, keyword, visibleCreators),
                page,
                size,
                query -> sysNoticeMapper.selectCount(query),
                query -> sysNoticeMapper.selectList(query).stream().map(this::toNoticeView).toList(),
                resolveNoticeSort(sortBy),
                resolveDirection(sortDirection, SORT_DESC)
        );
    }

    @Cacheable(value = "system:categories:all", key = "#root.target.currentTenantId()")
    public Map<String, List<CategoryOption>> categories() {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        return Map.of(
                "dict", loadCategoryOptions(tenantId, DICT_CATEGORY_PREFIX),
                "config", loadCategoryOptions(tenantId, CONFIG_CATEGORY_PREFIX)
        );
    }

    @Cacheable(value = "system:categories:target", key = "#root.target.generateCacheKey(new Object[]{#targetType})")
    public List<CategoryOption> categoryOptions(String targetType) {
        requireDatabaseMode();
        return loadCategoryOptions(currentTenantId(), prefixForTargetType(targetType));
    }

    public CategoryAnalysis analyzeCategoryOption(String targetType, String code) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysCategoryRuleEntity entity = getCategoryConfig(tenantId, targetType, code);
        List<String> matchers = splitMatchers(entity.getMatchers());
        int referenceCount = "dict".equalsIgnoreCase(targetType)
                ? countMatchingDicts(tenantId, matchers)
                : countMatchingConfigs(tenantId, matchers);
        List<String> sampleReferences = "dict".equalsIgnoreCase(targetType)
                ? sampleMatchingDicts(tenantId, matchers)
                : sampleMatchingConfigs(tenantId, matchers);
        List<CategoryAuditView> recentAudits = loadCategoryAudits(tenantId, targetType, code);
        List<CategoryTrendPoint> trend = buildCategoryTrend(recentAudits);
        return new CategoryAnalysis(
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
    @CacheEvict(value = {"system:categories:all", "system:categories:target", "system:dicts", "system:configs"}, allEntries = true)
    public CategoryOption createCategoryOption(String targetType, CategoryConfigRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
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
        auditService.record("SYSTEM_CATEGORY_CREATED", SecuritySupport.currentOperator(), tenantId, Map.of(
                "targetType", targetType,
                "code", request.code()
        ));
        return new CategoryOption(request.code(), request.name(), splitMatchers(entity.getMatchers()));
    }

    @Transactional
    @CacheEvict(value = {"system:categories:all", "system:categories:target", "system:dicts", "system:configs"}, allEntries = true)
    public CategoryOption updateCategoryOption(String targetType, String code, CategoryConfigRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysCategoryRuleEntity entity = getCategoryConfig(tenantId, targetType, code);
        entity.setCategoryName(request.name());
        entity.setMatchers(normalizeMatchers(request.matchers()));
        sysCategoryRuleMapper.updateById(entity);
        auditService.record("SYSTEM_CATEGORY_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of(
                "targetType", targetType,
                "code", code
        ));
        return new CategoryOption(code, request.name(), splitMatchers(entity.getMatchers()));
    }

    @Transactional
    @CacheEvict(value = {"system:categories:all", "system:categories:target", "system:dicts", "system:configs"}, allEntries = true)
    public void deleteCategoryOption(String targetType, String code) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysCategoryRuleEntity entity = getCategoryConfig(tenantId, targetType, code);
        sysCategoryRuleMapper.deleteById(entity.getId());
        auditService.record("SYSTEM_CATEGORY_DELETED", SecuritySupport.currentOperator(), tenantId, Map.of(
                "targetType", targetType,
                "code", code
        ));
    }

    @Transactional
    public NoticeView createNotice(NoticeCrudRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysNoticeEntity entity = new SysNoticeEntity();
        entity.setTenantId(tenantId);
        entity.setNoticeTitle(request.noticeTitle());
        entity.setNoticeContent(request.noticeContent());
        entity.setPublished(Boolean.TRUE.equals(request.published()) ? 1 : 0);
        entity.setPublishTime(request.publishTime());
        sysNoticeMapper.insert(entity);
        auditService.record("NOTICE_CREATED", operator, tenantId, Map.of("noticeId", entity.getId(), "workflowStatus", workflowStatus(entity)));
        return toNoticeView(entity);
    }

    @Transactional
    public NoticeView updateNotice(Long id, NoticeCrudRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysNoticeEntity entity = getNotice(id, tenantId);
        entity.setNoticeTitle(request.noticeTitle());
        entity.setNoticeContent(request.noticeContent());
        entity.setPublished(Boolean.TRUE.equals(request.published()) ? 1 : 0);
        entity.setPublishTime(request.publishTime());
        sysNoticeMapper.updateById(entity);
        auditService.record("NOTICE_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("noticeId", id, "workflowStatus", workflowStatus(entity)));
        return toNoticeView(entity);
    }

    @Transactional
    public void deleteNotice(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysNoticeEntity entity = getNotice(id, tenantId);
        sysNoticeMapper.deleteById(entity.getId());
        auditService.record("NOTICE_DELETED", SecuritySupport.currentOperator(), tenantId, Map.of("noticeId", id));
    }

    private DictView toDictView(SysDictEntity entity) {
        return new DictView(
                entity.getId(),
                entity.getDictType(),
                deriveCategory(currentTenantId(), DICT_CATEGORY_PREFIX, entity.getDictType()),
                entity.getDictCode(),
                entity.getDictValue(),
                entity.getCreatedBy()
        );
    }

    private ConfigView toConfigView(SysConfigEntity entity) {
        return new ConfigView(
                entity.getId(),
                entity.getConfigKey(),
                deriveCategory(currentTenantId(), CONFIG_CATEGORY_PREFIX, entity.getConfigKey()),
                entity.getConfigName(),
                entity.getConfigValue(),
                entity.getCreatedBy()
        );
    }

    private NoticeView toNoticeView(SysNoticeEntity entity) {
        return new NoticeView(
                entity.getId(),
                entity.getNoticeTitle(),
                entity.getNoticeContent(),
                entity.getPublished() != null && entity.getPublished() == 1,
                entity.getPublishTime(),
                workflowStatus(entity),
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
        applyCategoryFilter(query, tenantId, DICT_CATEGORY_PREFIX, category, SysDictEntity::getDictType);
        applyCreatorScope(query, visibleCreators, SysDictEntity::getCreatedBy);
        return query;
    }

    private LambdaQueryWrapper<SysConfigEntity> buildConfigQuery(
            String tenantId,
            String category,
            String keyword,
            Optional<Set<String>> visibleCreators
    ) {
        LambdaQueryWrapper<SysConfigEntity> query = new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getTenantId, tenantId)
                .eq(SysConfigEntity::getDeleted, 0)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysConfigEntity::getConfigKey, keyword)
                        .or()
                        .like(SysConfigEntity::getConfigName, keyword)
                        .or()
                        .like(SysConfigEntity::getConfigValue, keyword));
        applyCategoryFilter(query, tenantId, CONFIG_CATEGORY_PREFIX, category, SysConfigEntity::getConfigKey);
        applyCreatorScope(query, visibleCreators, SysConfigEntity::getCreatedBy);
        return query;
    }

    private LambdaQueryWrapper<SysNoticeEntity> buildNoticeQuery(
            String tenantId,
            Boolean published,
            String workflowStatus,
            String keyword,
            Optional<Set<String>> visibleCreators
    ) {
        LambdaQueryWrapper<SysNoticeEntity> query = new LambdaQueryWrapper<SysNoticeEntity>()
                .eq(SysNoticeEntity::getTenantId, tenantId)
                .eq(SysNoticeEntity::getDeleted, 0)
                .eq(published != null, SysNoticeEntity::getPublished, Boolean.TRUE.equals(published) ? 1 : 0)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysNoticeEntity::getNoticeTitle, keyword)
                        .or()
                        .like(SysNoticeEntity::getNoticeContent, keyword));
        if ("DRAFT".equalsIgnoreCase(workflowStatus)) {
            query.eq(SysNoticeEntity::getPublished, 0);
        } else if ("SCHEDULED".equalsIgnoreCase(workflowStatus)) {
            query.eq(SysNoticeEntity::getPublished, 1)
                    .isNotNull(SysNoticeEntity::getPublishTime)
                    .gt(SysNoticeEntity::getPublishTime, LocalDateTime.now());
        } else if ("PUBLISHED".equalsIgnoreCase(workflowStatus)) {
            query.eq(SysNoticeEntity::getPublished, 1)
                    .and(wrapper -> wrapper.isNull(SysNoticeEntity::getPublishTime)
                            .or()
                            .le(SysNoticeEntity::getPublishTime, LocalDateTime.now()));
        }
        applyCreatorScope(query, visibleCreators, SysNoticeEntity::getCreatedBy);
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

    private SysNoticeEntity getNotice(Long id, String tenantId) {
        SysNoticeEntity entity = sysNoticeMapper.selectOne(new LambdaQueryWrapper<SysNoticeEntity>()
                .eq(SysNoticeEntity::getId, id)
                .eq(SysNoticeEntity::getTenantId, tenantId)
                .eq(SysNoticeEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("公告不存在");
        }
        if (!dataScopeService.canAccessCreatedBy(tenantId, entity.getCreatedBy())) {
            throw new BusinessException("无权访问该公告");
        }
        return entity;
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled()
                || sysDictMapper == null
                || sysConfigMapper == null
                || sysNoticeMapper == null
                || sysAuditLogMapper == null
                || sysCategoryRuleMapper == null) {
            throw new BusinessException("当前未启用数据库系统管理能力");
        }
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
                        .eq(SysConfigEntity::getDeleted, 0)
                        )
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
                        .orderByAsc(SysDictEntity::getDictType)
                        .orderByAsc(SysDictEntity::getDictCode))
                .stream()
                .filter(item -> matchesAny(matchers, item.getDictType()))
                .map(item -> item.getDictType() + " / " + item.getDictCode())
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

    private List<CategoryAuditView> loadCategoryAudits(String tenantId, String targetType, String code) {
        List<String> eventTypes = List.of("SYSTEM_CATEGORY_CREATED", "SYSTEM_CATEGORY_UPDATED", "SYSTEM_CATEGORY_DELETED");
        return sysAuditLogMapper.selectList(new LambdaQueryWrapper<SysAuditLogEntity>()
                        .eq(SysAuditLogEntity::getTenantId, tenantId)
                        .in(SysAuditLogEntity::getEventType, eventTypes)
                        .like(SysAuditLogEntity::getPayloadJson, "\"" + "targetType" + "\":\"" + targetType + "\"")
                        .like(SysAuditLogEntity::getPayloadJson, "\"" + "code" + "\":\"" + code + "\"")
                        .orderByDesc(SysAuditLogEntity::getOccurredAt)
                        .last("limit 10"))
                .stream()
                .map(item -> new CategoryAuditView(
                        item.getEventType(),
                        item.getOperator(),
                        item.getOccurredAt(),
                        item.getPayloadJson()
                ))
                .toList();
    }

    private List<CategoryTrendPoint> buildCategoryTrend(List<CategoryAuditView> audits) {
        java.time.LocalDate today = java.time.LocalDate.now();
        Map<java.time.LocalDate, Long> counts = audits.stream()
                .filter(item -> item.occurredAt() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.occurredAt().toLocalDate(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        List<CategoryTrendPoint> trend = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate day = today.minusDays(i);
            trend.add(new CategoryTrendPoint(day.toString(), counts.getOrDefault(day, 0L).intValue()));
        }
        return trend;
    }

    public String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    public String generateCacheKey(Object... params) {
        StringBuilder key = new StringBuilder(currentTenantId());
        for (Object param : params) {
            key.append(':').append(param == null ? "" : param);
        }
        return key.toString();
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

    private SFunction<SysConfigEntity, ?> resolveConfigSort(String sortBy) {
        if ("configKey".equalsIgnoreCase(sortBy)) {
            return SysConfigEntity::getConfigKey;
        }
        if ("configName".equalsIgnoreCase(sortBy)) {
            return SysConfigEntity::getConfigName;
        }
        return SysConfigEntity::getCreatedAt;
    }

    private SFunction<SysNoticeEntity, ?> resolveNoticeSort(String sortBy) {
        if ("createdAt".equalsIgnoreCase(sortBy)) {
            return SysNoticeEntity::getCreatedAt;
        }
        if ("noticeTitle".equalsIgnoreCase(sortBy)) {
            return SysNoticeEntity::getNoticeTitle;
        }
        return SysNoticeEntity::getPublishTime;
    }

    private String resolveDirection(String sortDirection, String defaultValue) {
        return SORT_ASC.equalsIgnoreCase(sortDirection)
                ? SORT_ASC
                : SORT_DESC.equalsIgnoreCase(sortDirection) ? SORT_DESC : defaultValue;
    }

    private String deriveCategory(String tenantId, String prefix, String rawKey) {
        for (CategoryOption option : loadCategoryOptions(tenantId, prefix)) {
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

    private String workflowStatus(SysNoticeEntity entity) {
        boolean published = entity.getPublished() != null && entity.getPublished() == 1;
        if (!published) {
            return "DRAFT";
        }
        if (entity.getPublishTime() != null && entity.getPublishTime().isAfter(LocalDateTime.now())) {
            return "SCHEDULED";
        }
        return "PUBLISHED";
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

    private <E> void applyCategoryFilter(LambdaQueryWrapper<E> query, String tenantId, String prefix, String category, SFunction<E, ?> field) {
        if (!StringUtils.hasText(category)) {
            return;
        }
        CategoryOption configured = loadCategoryOptions(tenantId, prefix).stream()
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

    private List<CategoryOption> loadCategoryOptions(String tenantId, String prefix) {
        return sysCategoryRuleMapper.selectList(new LambdaQueryWrapper<SysCategoryRuleEntity>()
                        .eq(SysCategoryRuleEntity::getTenantId, tenantId)
                        .eq(SysCategoryRuleEntity::getTargetType, targetTypeFromPrefix(prefix))
                        .eq(SysCategoryRuleEntity::getDeleted, 0)
                        .orderByAsc(SysCategoryRuleEntity::getCategoryCode))
                .stream()
                .map(config -> new CategoryOption(
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

    @Schema(description = "字典项视图")
    public record DictView(
            @Schema(description = "字典 ID") Long id,
            @Schema(description = "字典类型") String dictType,
            @Schema(description = "字典分类") String category,
            @Schema(description = "字典编码") String dictCode,
            @Schema(description = "字典值") String dictValue,
            @Schema(description = "创建人") String createdBy
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "参数项视图")
    public record ConfigView(
            @Schema(description = "参数 ID") Long id,
            @Schema(description = "参数键") String configKey,
            @Schema(description = "参数分类") String category,
            @Schema(description = "参数名称") String configName,
            @Schema(description = "参数值") String configValue,
            @Schema(description = "创建人") String createdBy
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "公告视图")
    public record NoticeView(
            @Schema(description = "公告 ID") Long id,
            @Schema(description = "公告标题") String noticeTitle,
            @Schema(description = "公告内容") String noticeContent,
            @Schema(description = "是否发布") boolean published,
            @Schema(description = "发布时间") LocalDateTime publishTime,
            @Schema(description = "工作流状态") String workflowStatus,
            @Schema(description = "创建人") String createdBy
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "系统分类选项")
    public record CategoryOption(
            @Schema(description = "分类编码") String code,
            @Schema(description = "分类名称") String name,
            @Schema(description = "匹配规则") List<String> matchers
    ) implements Serializable {
        private static final long serialVersionUID = 1L;

        boolean matches(String rawKey) {
            if (!StringUtils.hasText(rawKey)) {
                return false;
            }
            for (String matcher : matchers) {
                if (!StringUtils.hasText(matcher)) {
                    continue;
                }
                if (matcher.endsWith("*")) {
                    if (rawKey.startsWith(matcher.substring(0, matcher.length() - 1))) {
                        return true;
                    }
                } else if (rawKey.equals(matcher)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Schema(description = "分类配置分析")
    public record CategoryAnalysis(
            @Schema(description = "分类编码") String code,
            @Schema(description = "分类名称") String name,
            @Schema(description = "目标类型") String targetType,
            @Schema(description = "匹配规则") List<String> matchers,
            @Schema(description = "引用数量") Integer referenceCount,
            @Schema(description = "引用样例") List<String> sampleReferences,
            @Schema(description = "最近审计记录") List<CategoryAuditView> recentAudits,
            @Schema(description = "七日趋势") List<CategoryTrendPoint> trend
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "分类配置审计记录")
    public record CategoryAuditView(
            @Schema(description = "事件类型") String eventType,
            @Schema(description = "操作人") String operator,
            @Schema(description = "发生时间") LocalDateTime occurredAt,
            @Schema(description = "审计负载") String payloadJson
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "分类配置趋势点")
    public record CategoryTrendPoint(
            @Schema(description = "日期") String date,
            @Schema(description = "次数") Integer count
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
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
