package com.enterprise.auth.platform.modules.codegen.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("codegen_template")
@Data
public class CodegenTemplateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String name;
    private String language;
    private String templateCategory;
    private String pathPattern;
    private String content;
    private String description;
    private Integer builtin;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}