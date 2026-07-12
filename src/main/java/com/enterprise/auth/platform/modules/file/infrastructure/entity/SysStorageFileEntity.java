package com.enterprise.auth.platform.modules.file.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@TableName("sys_storage_file")
@Data
public class SysStorageFileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String fileKey;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String storageType;
    private String bucketName;
    private String objectKey;
    private String etag;
    private String visibility;
    private Long ownerUserId;
    /** READY / PENDING / FAILED / DELETE_PENDING */
    private String lifecycleStatus;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}