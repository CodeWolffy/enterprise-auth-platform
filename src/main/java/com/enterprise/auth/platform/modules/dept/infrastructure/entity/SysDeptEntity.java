package com.enterprise.auth.platform.modules.dept.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("sys_dept")
@Data
public class SysDeptEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long parentId;
    private String deptCode;
    private String deptName;
    private Long leaderUserId;
    private String leaderName;
    private String leaderPhone;
    private Integer orderNo;
    private Integer enabled;
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
