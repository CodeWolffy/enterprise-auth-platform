package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysDictEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysDictValueEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysDictMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysDictValueMapper;
import com.enterprise.auth.platform.modules.system.interfaces.DictValueCrudRequest;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DictValueApplicationService {

    private static final int DICT_LABEL_MAX_LENGTH = 128;
    private static final int DICT_VALUE_MAX_LENGTH = 255;
    private static final int SHOW_CLASS_MAX_LENGTH = 100;
    private static final int REMARKS_MAX_LENGTH = 255;

    private final SysDictValueMapper sysDictValueMapper;
    private final SysDictMapper sysDictMapper;
    private final DataScopeService dataScopeService;

    public DictValueApplicationService(
            SysDictValueMapper sysDictValueMapper,
            SysDictMapper sysDictMapper,
            DataScopeService dataScopeService
    ) {
        this.sysDictValueMapper = sysDictValueMapper;
        this.sysDictMapper = sysDictMapper;
        this.dataScopeService = dataScopeService;
    }

    @Cacheable(value = CacheNames.SYSTEM_DICTS, key = "'value:' + #dictType")
    public List<SystemViewModels.DictValueView> listByType(String dictType) {
        String tenantId = currentTenantId();
        String normalizedDictType = normalizeRequired(dictType, "字典类型不能为空", 64, "字典类型长度不能超过64个字符");
        return sysDictValueMapper.selectList(new LambdaQueryWrapper<SysDictValueEntity>()
                .eq(SysDictValueEntity::getTenantId, tenantId)
                .eq(SysDictValueEntity::getDictType, normalizedDictType)
                .eq(SysDictValueEntity::getDeleted, 0)
                .eq(SysDictValueEntity::getEnabled, 1)
                .orderByAsc(SysDictValueEntity::getSort)
                .orderByAsc(SysDictValueEntity::getId))
                .stream()
                .map(this::toValueView)
                .toList();
    }

    public List<SystemViewModels.DictValueView> listByDictId(Long dictId) {
        SysDictEntity dict = getDict(dictId);
        return listEntitiesByDictId(dict.getId()).stream().map(this::toValueView).toList();
    }

    public SystemViewModels.DictValueView detail(Long valueId) {
        return toValueView(getValue(valueId));
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, allEntries = true)
    public SystemViewModels.DictValueView create(Long dictId, DictValueCrudRequest request) {
        SysDictEntity dict = getDict(dictId);
        String dictValue = normalizeRequired(request.dictValue(), "字典键值不能为空", DICT_VALUE_MAX_LENGTH, "字典键值长度不能超过255个字符");
        ensureDictValueUnique(dict.getTenantId(), dict.getId(), dictValue, null);

        SysDictValueEntity entity = new SysDictValueEntity();
        entity.setTenantId(currentTenantId());
        entity.setDictId(dict.getId());
        entity.setDictType(dict.getDictType());
        entity.setDictLabel(normalizeRequired(request.dictLabel(), "字典标签不能为空", DICT_LABEL_MAX_LENGTH, "字典标签长度不能超过128个字符"));
        entity.setDictValue(dictValue);
        entity.setSort(request.sort() == null ? nextSort(dict.getId()) : request.sort());
        entity.setShowClass(normalizeOptional(request.showClass(), SHOW_CLASS_MAX_LENGTH, "回显样式长度不能超过100个字符"));
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        entity.setRemarks(normalizeOptional(request.remarks(), REMARKS_MAX_LENGTH, "备注长度不能超过255个字符"));
        sysDictValueMapper.insert(entity);
        return toValueView(entity);
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, allEntries = true)
    public SystemViewModels.DictValueView update(Long valueId, DictValueCrudRequest request) {
        SysDictValueEntity entity = getValue(valueId);
        SysDictEntity dict = getDict(entity.getDictId());
        String dictValue = normalizeRequired(request.dictValue(), "字典键值不能为空", DICT_VALUE_MAX_LENGTH, "字典键值长度不能超过255个字符");
        ensureDictValueUnique(dict.getTenantId(), dict.getId(), dictValue, valueId);

        entity.setDictType(dict.getDictType());
        entity.setDictLabel(normalizeRequired(request.dictLabel(), "字典标签不能为空", DICT_LABEL_MAX_LENGTH, "字典标签长度不能超过128个字符"));
        entity.setDictValue(dictValue);
        entity.setSort(request.sort() == null ? 0 : request.sort());
        entity.setShowClass(normalizeOptional(request.showClass(), SHOW_CLASS_MAX_LENGTH, "回显样式长度不能超过100个字符"));
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        entity.setRemarks(normalizeOptional(request.remarks(), REMARKS_MAX_LENGTH, "备注长度不能超过255个字符"));
        sysDictValueMapper.updateById(entity);
        return toValueView(entity);
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, allEntries = true)
    public void delete(Long valueId) {
        SysDictValueEntity entity = getValue(valueId);
        releaseDeletedValueIdentity(entity);
        sysDictValueMapper.updateById(entity);
        sysDictValueMapper.deleteById(entity.getId());
    }

    @CacheEvict(value = CacheNames.SYSTEM_DICTS, allEntries = true)
    public String refreshCache() {
        return "system:dicts";
    }

    private SysDictEntity getDict(Long dictId) {
        SysDictEntity entity = sysDictMapper.selectOne(new LambdaQueryWrapper<SysDictEntity>()
                .eq(SysDictEntity::getTenantId, currentTenantId())
                .eq(SysDictEntity::getId, dictId)
                .eq(SysDictEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("字典项不存在");
        }
        if (!dataScopeService.canAccessCreatedBy(entity.getTenantId(), entity.getCreatedBy())) {
            throw new BusinessException("无权访问该字典项");
        }
        return entity;
    }

    private SysDictValueEntity getValue(Long valueId) {
        SysDictValueEntity entity = sysDictValueMapper.selectOne(new LambdaQueryWrapper<SysDictValueEntity>()
                .eq(SysDictValueEntity::getTenantId, currentTenantId())
                .eq(SysDictValueEntity::getId, valueId)
                .eq(SysDictValueEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("字典值不存在");
        }
        getDict(entity.getDictId());
        return entity;
    }

    private List<SysDictValueEntity> listEntitiesByDictId(Long dictId) {
        return sysDictValueMapper.selectList(new LambdaQueryWrapper<SysDictValueEntity>()
                .eq(SysDictValueEntity::getTenantId, currentTenantId())
                .eq(SysDictValueEntity::getDictId, dictId)
                .eq(SysDictValueEntity::getDeleted, 0)
                .orderByAsc(SysDictValueEntity::getSort)
                .orderByAsc(SysDictValueEntity::getId));
    }

    private int nextSort(Long dictId) {
        return listEntitiesByDictId(dictId).stream()
                .map(SysDictValueEntity::getSort)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(0);
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
                value.getUpdatedAt()
        );
    }

    private void ensureDictValueUnique(String tenantId, Long dictId, String dictValue, Long selfId) {
        Long count = sysDictValueMapper.selectCount(new LambdaQueryWrapper<SysDictValueEntity>()
                .eq(SysDictValueEntity::getTenantId, tenantId)
                .eq(SysDictValueEntity::getDictId, dictId)
                .eq(SysDictValueEntity::getDeleted, 0)
                .eq(SysDictValueEntity::getDictValue, dictValue)
                .ne(selfId != null, SysDictValueEntity::getId, selfId == null ? -1L : selfId));
        if (count != null && count > 0) {
            throw new BusinessException("CONFLICT", "字典键值已存在");
        }
    }

    private void releaseDeletedValueIdentity(SysDictValueEntity entity) {
        entity.setDictValue(tombstone(entity.getDictValue(), entity.getId(), DICT_VALUE_MAX_LENGTH));
    }

    private String normalizeRequired(String value, String message, int maxLength, String lengthMessage) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException(message);
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

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}
