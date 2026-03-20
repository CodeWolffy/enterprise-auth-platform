package com.enterprise.auth.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysConfigEntity;
import com.enterprise.auth.platform.persistence.entity.SysDictEntity;
import com.enterprise.auth.platform.persistence.entity.SysNoticeEntity;
import com.enterprise.auth.platform.persistence.mapper.SysConfigMapper;
import com.enterprise.auth.platform.persistence.mapper.SysDictMapper;
import com.enterprise.auth.platform.persistence.mapper.SysNoticeMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.system.dto.ConfigCrudRequest;
import com.enterprise.auth.platform.system.dto.DictCrudRequest;
import com.enterprise.auth.platform.system.dto.NoticeCrudRequest;
import com.enterprise.auth.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysDictMapper sysDictMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysNoticeMapper sysNoticeMapper;
    private final AuditService auditService;

    public SystemManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysDictMapper sysDictMapper,
            @Nullable SysConfigMapper sysConfigMapper,
            @Nullable SysNoticeMapper sysNoticeMapper,
            AuditService auditService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysDictMapper = sysDictMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.sysNoticeMapper = sysNoticeMapper;
        this.auditService = auditService;
    }

    public List<DictView> dicts() {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        return sysDictMapper.selectList(new LambdaQueryWrapper<SysDictEntity>()
                        .eq(SysDictEntity::getTenantId, tenantId)
                        .eq(SysDictEntity::getDeleted, 0)
                        .orderByAsc(SysDictEntity::getId))
                .stream()
                .map(item -> new DictView(item.getId(), item.getDictType(), item.getDictCode(), item.getDictValue()))
                .toList();
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
        return new DictView(entity.getId(), entity.getDictType(), entity.getDictCode(), entity.getDictValue());
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
        return new DictView(entity.getId(), entity.getDictType(), entity.getDictCode(), entity.getDictValue());
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

    public List<ConfigView> configs() {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigEntity>()
                        .eq(SysConfigEntity::getTenantId, tenantId)
                        .eq(SysConfigEntity::getDeleted, 0)
                        .orderByAsc(SysConfigEntity::getId))
                .stream()
                .map(item -> new ConfigView(item.getId(), item.getConfigKey(), item.getConfigName(), item.getConfigValue()))
                .toList();
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
        return new ConfigView(entity.getId(), entity.getConfigKey(), entity.getConfigName(), entity.getConfigValue());
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
        return new ConfigView(entity.getId(), entity.getConfigKey(), entity.getConfigName(), entity.getConfigValue());
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

    public List<NoticeView> notices() {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        return sysNoticeMapper.selectList(new LambdaQueryWrapper<SysNoticeEntity>()
                        .eq(SysNoticeEntity::getTenantId, tenantId)
                        .eq(SysNoticeEntity::getDeleted, 0)
                        .orderByDesc(SysNoticeEntity::getId))
                .stream()
                .map(item -> new NoticeView(
                        item.getId(),
                        item.getNoticeTitle(),
                        item.getNoticeContent(),
                        item.getPublished() != null && item.getPublished() == 1,
                        item.getPublishTime()
                ))
                .toList();
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
        return new NoticeView(
                entity.getId(),
                entity.getNoticeTitle(),
                entity.getNoticeContent(),
                entity.getPublished() == 1,
                entity.getPublishTime()
        );
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
        return new NoticeView(
                entity.getId(),
                entity.getNoticeTitle(),
                entity.getNoticeContent(),
                entity.getPublished() == 1,
                entity.getPublishTime()
        );
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

    private SysDictEntity getDict(Long id, String tenantId) {
        SysDictEntity entity = sysDictMapper.selectOne(new LambdaQueryWrapper<SysDictEntity>()
                .eq(SysDictEntity::getId, id)
                .eq(SysDictEntity::getTenantId, tenantId)
                .eq(SysDictEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("字典项不存在");
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
        return entity;
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled() || sysDictMapper == null || sysConfigMapper == null || sysNoticeMapper == null) {
            throw new BusinessException("当前为默认内存模式，暂未启用数据库写入能力");
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    @Schema(description = "字典项视图")
    public record DictView(
            @Schema(description = "字典ID") Long id,
            @Schema(description = "字典类型") String dictType,
            @Schema(description = "字典编码") String dictCode,
            @Schema(description = "字典值") String dictValue
    ) {
    }

    @Schema(description = "参数项视图")
    public record ConfigView(
            @Schema(description = "参数ID") Long id,
            @Schema(description = "参数键") String configKey,
            @Schema(description = "参数名称") String configName,
            @Schema(description = "参数值") String configValue
    ) {
    }

    @Schema(description = "公告视图")
    public record NoticeView(
            @Schema(description = "公告ID") Long id,
            @Schema(description = "公告标题") String noticeTitle,
            @Schema(description = "公告内容") String noticeContent,
            @Schema(description = "是否发布") boolean published,
            @Schema(description = "发布时间") LocalDateTime publishTime
    ) {
    }
}
