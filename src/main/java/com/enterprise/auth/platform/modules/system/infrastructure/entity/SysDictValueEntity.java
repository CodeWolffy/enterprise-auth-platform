package com.enterprise.auth.platform.modules.system.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.Instant;

@TableName("sys_dict_value")
@Data
public class SysDictValueEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long dictId;
    private String dictType;
    private String dictLabel;
    private String dictValue;
    private String showClass;
    private Integer sort;
    private Integer enabled;
    private String remarks;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}