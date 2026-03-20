package com.enterprise.auth.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysConfigEntity;
import com.enterprise.auth.platform.persistence.entity.SysDictEntity;
import com.enterprise.auth.platform.persistence.entity.SysNoticeEntity;
import com.enterprise.auth.platform.persistence.mapper.SysConfigMapper;
import com.enterprise.auth.platform.persistence.mapper.SysDictMapper;
import com.enterprise.auth.platform.persistence.mapper.SysNoticeMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.system.dto.ConfigCrudRequest;
import com.enterprise.auth.platform.system.dto.DictCrudRequest;
import com.enterprise.auth.platform.system.dto.NoticeCrudRequest;
import com.enterprise.auth.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemManagementService {

    private static final String SORT_ASC = "asc";
    private static final String SORT_DESC = "desc";

    private final PersistenceProperties persistenceProperties;
    private final SysDictMapper sysDictMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysNoticeMapper sysNoticeMapper;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;

    public SystemManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysDictMapper sysDictMapper,
            @Nullable SysConfigMapper sysConfigMapper,
            @Nullable SysNoticeMapper sysNoticeMapper,
            AuditService auditService,
            DataScopeService dataScopeService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysDictMapper = sysDictMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.sysNoticeMapper = sysNoticeMapper;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<DictView> dicts(String dictType, String keyword, int page, int size) {
        return dicts(dictType, keyword, page, size, null, null);
    }

    public PageResult<DictView> dicts(String dictType, String keyword, int page, int size, String sortBy, String sortDirection) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        Optional<Set<String>> visibleCreators = dataScopeService.visibleUsernames(tenantId);
        return pageQuery(
                buildDictQuery(tenantId, dictType, keyword, visibleCreators),
                buildDictQuery(tenantId, dictType, keyword, visibleCreators),
                page,
                size,
                query -> sysDictMapper.selectCount(query),
                query -> sysDictMapper.selectList(query).stream().map(this::toDictView).toList(),
                resolveDictSort(sortBy),
                resolveDirection(sortDirection, SORT_ASC)
        );
    }

    public PageResult<DictView> dicts() {
        return dicts(null, null, 1, 10, null, null);
    }

    @Transactional
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
    public void deleteDict(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysDictEntity entity = getDict(id, tenantId);
        sysDictMapper.deleteById(entity.getId());
        auditService.record("DICT_DELETED", operator, tenantId, Map.of("dictId", id));
    }

    public PageResult<ConfigView> configs(String keyword, int page, int size) {
        return configs(keyword, page, size, null, null);
    }

    public PageResult<ConfigView> configs(String keyword, int page, int size, String sortBy, String sortDirection) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        Optional<Set<String>> visibleCreators = dataScopeService.visibleUsernames(tenantId);
        return pageQuery(
                buildConfigQuery(tenantId, keyword, visibleCreators),
                buildConfigQuery(tenantId, keyword, visibleCreators),
                page,
                size,
                query -> sysConfigMapper.selectCount(query),
                query -> sysConfigMapper.selectList(query).stream().map(this::toConfigView).toList(),
                resolveConfigSort(sortBy),
                resolveDirection(sortDirection, SORT_ASC)
        );
    }

    public PageResult<ConfigView> configs() {
        return configs(null, 1, 10, null, null);
    }

    @Transactional
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
    public void deleteConfig(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysConfigEntity entity = getConfig(id, tenantId);
        sysConfigMapper.deleteById(entity.getId());
        auditService.record("CONFIG_DELETED", operator, tenantId, Map.of("configId", id));
    }

    public PageResult<NoticeView> notices(Boolean published, String keyword, int page, int size) {
        return notices(published, keyword, page, size, null, null);
    }

    public PageResult<NoticeView> notices(
            Boolean published,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        Optional<Set<String>> visibleCreators = dataScopeService.visibleUsernames(tenantId);
        return pageQuery(
                buildNoticeQuery(tenantId, published, keyword, visibleCreators),
                buildNoticeQuery(tenantId, published, keyword, visibleCreators),
                page,
                size,
                query -> sysNoticeMapper.selectCount(query),
                query -> sysNoticeMapper.selectList(query).stream().map(this::toNoticeView).toList(),
                resolveNoticeSort(sortBy),
                resolveDirection(sortDirection, SORT_DESC)
        );
    }

    public PageResult<NoticeView> notices() {
        return notices(null, null, 1, 10, null, null);
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
        auditService.record("NOTICE_CREATED", operator, tenantId, Map.of("noticeId", entity.getId()));
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
        auditService.record("NOTICE_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("noticeId", id));
        return toNoticeView(entity);
    }

    @Transactional
    public void deleteNotice(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysNoticeEntity entity = getNotice(id, tenantId);
        sysNoticeMapper.deleteById(entity.getId());
        auditService.record("NOTICE_DELETED", operator, tenantId, Map.of("noticeId", id));
    }

    private DictView toDictView(SysDictEntity entity) {
        return new DictView(entity.getId(), entity.getDictType(), entity.getDictCode(), entity.getDictValue(), entity.getCreatedBy());
    }

    private ConfigView toConfigView(SysConfigEntity entity) {
        return new ConfigView(entity.getId(), entity.getConfigKey(), entity.getConfigName(), entity.getConfigValue(), entity.getCreatedBy());
    }

    private NoticeView toNoticeView(SysNoticeEntity entity) {
        return new NoticeView(
                entity.getId(),
                entity.getNoticeTitle(),
                entity.getNoticeContent(),
                entity.getPublished() != null && entity.getPublished() == 1,
                entity.getPublishTime(),
                entity.getCreatedBy()
        );
    }

    private LambdaQueryWrapper<SysDictEntity> buildDictQuery(
            String tenantId,
            String dictType,
            String keyword,
            Optional<Set<String>> visibleCreators
    ) {
        LambdaQueryWrapper<SysDictEntity> query = new LambdaQueryWrapper<SysDictEntity>()
                .eq(SysDictEntity::getTenantId, tenantId)
                .eq(SysDictEntity::getDeleted, 0)
                .like(StringUtils.hasText(dictType), SysDictEntity::getDictType, dictType)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysDictEntity::getDictCode, keyword)
                        .or()
                        .like(SysDictEntity::getDictValue, keyword));
        visibleCreators.ifPresent(usernames -> {
            if (usernames.isEmpty()) {
                query.apply("1 = 0");
                return;
            }
            query.in(SysDictEntity::getCreatedBy, usernames);
        });
        return query;
    }

    private LambdaQueryWrapper<SysConfigEntity> buildConfigQuery(
            String tenantId,
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
        visibleCreators.ifPresent(usernames -> {
            if (usernames.isEmpty()) {
                query.apply("1 = 0");
                return;
            }
            query.in(SysConfigEntity::getCreatedBy, usernames);
        });
        return query;
    }

    private LambdaQueryWrapper<SysNoticeEntity> buildNoticeQuery(
            String tenantId,
            Boolean published,
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
        visibleCreators.ifPresent(usernames -> {
            if (usernames.isEmpty()) {
                query.apply("1 = 0");
                return;
            }
            query.in(SysNoticeEntity::getCreatedBy, usernames);
        });
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
        if (!persistenceProperties.databaseEnabled() || sysDictMapper == null || sysConfigMapper == null || sysNoticeMapper == null) {
            throw new BusinessException("当前未启用数据库系统管理能力");
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
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

    @Schema(description = "字典项视图")
    public record DictView(
            @Schema(description = "字典 ID") Long id,
            @Schema(description = "字典类型") String dictType,
            @Schema(description = "字典编码") String dictCode,
            @Schema(description = "字典值") String dictValue,
            @Schema(description = "创建人") String createdBy
    ) {
    }

    @Schema(description = "参数项视图")
    public record ConfigView(
            @Schema(description = "参数 ID") Long id,
            @Schema(description = "参数键") String configKey,
            @Schema(description = "参数名称") String configName,
            @Schema(description = "参数值") String configValue,
            @Schema(description = "创建人") String createdBy
    ) {
    }

    @Schema(description = "公告视图")
    public record NoticeView(
            @Schema(description = "公告 ID") Long id,
            @Schema(description = "公告标题") String noticeTitle,
            @Schema(description = "公告内容") String noticeContent,
            @Schema(description = "是否发布") boolean published,
            @Schema(description = "发布时间") LocalDateTime publishTime,
            @Schema(description = "创建人") String createdBy
    ) {
    }
}
