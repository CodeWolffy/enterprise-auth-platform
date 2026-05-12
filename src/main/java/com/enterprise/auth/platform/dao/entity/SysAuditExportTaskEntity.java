package com.enterprise.auth.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_audit_export_task")
@Data
public class SysAuditExportTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String operator;
    private String status;
    private String fileName;
    private Integer recordCount;
    private String queryJson;
    private String errorMessage;
    private byte[] fileContent;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    public byte[] getFileContent() { return fileContent; }
    public void setFileContent(byte[] fileContent) { this.fileContent = fileContent; }
}
