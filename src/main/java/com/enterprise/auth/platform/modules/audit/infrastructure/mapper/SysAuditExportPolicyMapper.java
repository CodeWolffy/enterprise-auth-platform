package com.enterprise.auth.platform.modules.audit.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.audit.infrastructure.entity.SysAuditExportPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysAuditExportPolicyMapper extends BaseMapper<SysAuditExportPolicyEntity> {
}
