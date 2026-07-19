package com.enterprise.auth.platform.modules.file.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import com.enterprise.auth.platform.modules.file.infrastructure.mapper.SysStorageFileMapper;
import com.enterprise.auth.platform.modules.file.infrastructure.mapper.FileStorageStatsRow;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FileStatsFacade {

    private final SysStorageFileMapper sysStorageFileMapper;

    public FileStatsFacade(SysStorageFileMapper sysStorageFileMapper) {
        this.sysStorageFileMapper = sysStorageFileMapper;
    }

    public long countFiles(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        return sysStorageFileMapper.selectCount(fileScope(tenantId, platformScope, visibleUserIds));
    }

    public long sumStorageBytes(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        Long totalSize = sysStorageFileMapper.sumFileSize(
                tenantId,
                platformScope,
                visibleUserIds.orElse(null)
        );
        return totalSize == null ? 0L : Math.max(totalSize, 0L);
    }

    public StorageStats storageStats(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        FileStorageStatsRow row = sysStorageFileMapper.aggregateStorageStats(
                tenantId,
                platformScope,
                visibleUserIds.orElse(null)
        );
        if (row == null) {
            return new StorageStats(0L, 0L);
        }
        return new StorageStats(nonNegative(row.getFileCount()), nonNegative(row.getTotalBytes()));
    }

    private long nonNegative(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    public record StorageStats(long fileCount, long totalBytes) {
    }

    private LambdaQueryWrapper<SysStorageFileEntity> fileScope(
            String tenantId,
            boolean platformScope,
            Optional<Set<Long>> visibleUserIds
    ) {
        LambdaQueryWrapper<SysStorageFileEntity> wrapper = new LambdaQueryWrapper<SysStorageFileEntity>()
                .eq(SysStorageFileEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysStorageFileEntity::getTenantId, tenantId);
            applyVisibleIds(wrapper, visibleUserIds, SysStorageFileEntity::getOwnerUserId);
        }
        return wrapper;
    }

    private <T> void applyVisibleIds(
            LambdaQueryWrapper<T> wrapper,
            Optional<Set<Long>> visibleIds,
            SFunction<T, ?> column
    ) {
        visibleIds.ifPresent(ids -> {
            if (ids.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(column, ids);
            }
        });
    }
}
