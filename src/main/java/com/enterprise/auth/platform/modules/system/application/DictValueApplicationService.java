package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
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

    private final SysDictValueMapper sysDictValueMapper;
    private final SysDictMapper sysDictMapper;

    public DictValueApplicationService(SysDictValueMapper sysDictValueMapper, SysDictMapper sysDictMapper) {
        this.sysDictValueMapper = sysDictValueMapper;
        this.sysDictMapper = sysDictMapper;
    }

    @Cacheable(value = CacheNames.SYSTEM_DICTS, key = "'value:' + #dictType")
    public List<SystemViewModels.DictValueView> listByType(String dictType) {
        String tenantId = currentTenantId();
        return sysDictValueMapper.selectList(new LambdaQueryWrapper<SysDictValueEntity>()
                .eq(SysDictValueEntity::getTenantId, tenantId)
                .eq(SysDictValueEntity::getDictType, dictType)
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

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, allEntries = true)
    public SystemViewModels.DictValueView create(Long dictId, DictValueCrudRequest request) {
        SysDictEntity dict = getDict(dictId);
        SysDictValueEntity entity = new SysDictValueEntity();
        entity.setTenantId(currentTenantId());
        entity.setDictId(dict.getId());
        entity.setDictType(dict.getDictType());
        entity.setDictLabel(normalizeRequired(request.dictLabel(), "字典标签不能为空"));
        entity.setDictValue(normalizeRequired(request.dictValue(), "字典键值不能为空"));
        entity.setSort(request.sort() == null ? nextSort(dict.getId()) : request.sort());
        entity.setShowClass(blankToNull(request.showClass()));
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        entity.setRemarks(blankToNull(request.remarks()));
        sysDictValueMapper.insert(entity);
        return toValueView(entity);
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, allEntries = true)
    public SystemViewModels.DictValueView update(Long valueId, DictValueCrudRequest request) {
        SysDictValueEntity entity = getValue(valueId);
        SysDictEntity dict = getDict(entity.getDictId());
        entity.setDictType(dict.getDictType());
        entity.setDictLabel(normalizeRequired(request.dictLabel(), "字典标签不能为空"));
        entity.setDictValue(normalizeRequired(request.dictValue(), "字典键值不能为空"));
        entity.setSort(request.sort() == null ? 0 : request.sort());
        entity.setShowClass(blankToNull(request.showClass()));
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        entity.setRemarks(blankToNull(request.remarks()));
        sysDictValueMapper.updateById(entity);
        return toValueView(entity);
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, allEntries = true)
    public void delete(Long valueId) {
        SysDictValueEntity entity = getValue(valueId);
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
                TimeSupport.toEpochMilli(value.getUpdatedAt())
        );
    }

    private String normalizeRequired(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}