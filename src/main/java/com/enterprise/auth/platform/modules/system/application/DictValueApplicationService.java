package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysDictValueEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysDictMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysDictValueMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DictValueApplicationService {

    private final SysDictValueMapper sysDictValueMapper;
    private final SysDictMapper sysDictMapper;

    public DictValueApplicationService(SysDictValueMapper sysDictValueMapper, SysDictMapper sysDictMapper) {
        this.sysDictValueMapper = sysDictValueMapper;
        this.sysDictMapper = sysDictMapper;
    }

    @Cacheable(value = CacheNames.SYSTEM_DICTS, key = "'value:' + #dictType")
    public List<SysDictValueEntity> listByType(String dictType) {
        String tenantId = currentTenantId();
        return sysDictValueMapper.selectList(new LambdaQueryWrapper<SysDictValueEntity>()
                .eq(SysDictValueEntity::getTenantId, tenantId)
                .eq(SysDictValueEntity::getDictType, dictType)
                .eq(SysDictValueEntity::getDeleted, 0)
                .eq(SysDictValueEntity::getEnabled, 1)
                .orderByAsc(SysDictValueEntity::getSort));
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, key = "'value:' + #dictType")
    public SysDictValueEntity create(String dictType, Long dictId, String label, String value, Integer sort, String showClass) {
        String tenantId = currentTenantId();
        SysDictValueEntity entity = new SysDictValueEntity();
        entity.setTenantId(tenantId);
        entity.setDictId(dictId);
        entity.setDictType(dictType);
        entity.setDictLabel(label.trim());
        entity.setDictValue(value.trim());
        entity.setSort(sort != null ? sort : 0);
        entity.setShowClass(showClass);
        entity.setEnabled(1);
        sysDictValueMapper.insert(entity);
        return entity;
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, key = "'value:' + #dictType")
    public SysDictValueEntity update(String dictType, Long valueId, String label, String value, Integer sort, String showClass) {
        String tenantId = currentTenantId();
        SysDictValueEntity entity = sysDictValueMapper.selectById(valueId);
        if (entity == null || !entity.getTenantId().equals(tenantId)) {
            throw new BusinessException("字典值不存在");
        }
        entity.setDictLabel(label.trim());
        entity.setDictValue(value.trim());
        if (sort != null) entity.setSort(sort);
        entity.setShowClass(showClass);
        sysDictValueMapper.updateById(entity);
        return entity;
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_DICTS, key = "'value:' + #dictType")
    public void delete(String dictType, Long valueId) {
        String tenantId = currentTenantId();
        SysDictValueEntity entity = sysDictValueMapper.selectById(valueId);
        if (entity == null || !entity.getTenantId().equals(tenantId)) {
            throw new BusinessException("字典值不存在");
        }
        sysDictValueMapper.deleteById(valueId);
    }

    @CacheEvict(value = CacheNames.SYSTEM_DICTS, allEntries = true)
    public void refreshCache() {
        // 清除所有字典缓存
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}