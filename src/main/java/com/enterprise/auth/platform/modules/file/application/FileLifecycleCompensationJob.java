package com.enterprise.auth.platform.modules.file.application;

import com.enterprise.auth.platform.modules.file.domain.FileLifecycleStatus;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import com.enterprise.auth.platform.modules.file.infrastructure.mapper.SysStorageFileMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 文件生命周期补偿：清理 FAILED 孤儿对象、完成 DELETE_PENDING 删除。
 */
@Component
public class FileLifecycleCompensationJob {

    private static final Logger log = LoggerFactory.getLogger(FileLifecycleCompensationJob.class);
    private static final int BATCH = 50;

    private final SysStorageFileMapper storageFileMapper;
    private final ObjectStorageService objectStorageService;

    public FileLifecycleCompensationJob(
            SysStorageFileMapper storageFileMapper,
            ObjectStorageService objectStorageService
    ) {
        this.storageFileMapper = storageFileMapper;
        this.objectStorageService = objectStorageService;
    }

    @Scheduled(fixedDelayString = "${platform.file.compensation-interval-ms:15000}")
    public void compensate() {
        processDeletePending();
        processFailed();
    }

    private void processDeletePending() {
        List<SysStorageFileEntity> rows = storageFileMapper.selectByLifecycle(
                FileLifecycleStatus.DELETE_PENDING.name(),
                BATCH
        );
        for (SysStorageFileEntity entity : rows) {
            try {
                if (StringUtils.hasText(entity.getObjectKey())) {
                    objectStorageService.delete(entity.getBucketName(), entity.getObjectKey());
                }
                storageFileMapper.softDeleteByIdIgnoreTenant(entity.getId());
            } catch (Exception ex) {
                log.warn("删除文件补偿失败。fileKey={}, error={}", entity.getFileKey(), ex.getMessage());
            }
        }
    }

    private void processFailed() {
        List<SysStorageFileEntity> rows = storageFileMapper.selectByLifecycle(
                FileLifecycleStatus.FAILED.name(),
                BATCH
        );
        for (SysStorageFileEntity entity : rows) {
            try {
                if (StringUtils.hasText(entity.getObjectKey()) && StringUtils.hasText(entity.getBucketName())) {
                    try {
                        objectStorageService.delete(entity.getBucketName(), entity.getObjectKey());
                    } catch (Exception ignored) {
                        // 对象可能本就不存在
                    }
                }
                storageFileMapper.softDeleteByIdIgnoreTenant(entity.getId());
            } catch (Exception ex) {
                log.warn("失败文件清理异常。fileKey={}, error={}", entity.getFileKey(), ex.getMessage());
            }
        }
    }
}