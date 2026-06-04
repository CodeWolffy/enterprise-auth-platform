package com.enterprise.auth.platform.modules.workflow.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.audit.AuditEventPublisher;
import com.enterprise.auth.platform.common.audit.PlatformAuditEvent;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskUrgeEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskUrgeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkflowTaskUrgeService {

    private final WfTaskUrgeMapper urgeMapper;
    private final WfTaskMapper taskMapper;
    private final CurrentUserService currentUserService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private static final TypeReference<java.util.Set<Long>> LONG_SET_TYPE = new TypeReference<>() { };

    public WorkflowTaskUrgeService(
            WfTaskUrgeMapper urgeMapper,
            WfTaskMapper taskMapper,
            CurrentUserService currentUserService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.urgeMapper = urgeMapper;
        this.taskMapper = taskMapper;
        this.currentUserService = currentUserService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowTaskUrgeResult urge(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        WfTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getId, taskId)
                .eq(WfTaskEntity::getTenantId, user.tenantId())
                .eq(WfTaskEntity::getStatus, "PENDING")
                .eq(WfTaskEntity::getDeleted, 0)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException("NOT_FOUND", "待办任务不存在或已结束");
        }
        if (!canUrge(task, user)) {
            throw new BusinessException("ACCESS_DENIED", "无权催办该任务");
        }

        WfTaskUrgeEntity entity = new WfTaskUrgeEntity();
        entity.setTenantId(user.tenantId());
        entity.setTaskId(task.getId());
        entity.setInstanceId(task.getInstanceId());
        entity.setUrgedByUserId(user.id());
        entity.setUrgedByUsername(user.username());
        entity.setComment(StringUtils.hasText(command.comment()) ? command.comment().trim() : null);
        entity.setUrgedAt(TimeSupport.utcNowDateTime());
        urgeMapper.insert(entity);

        Set<String> targets = new LinkedHashSet<>();
        if (task.getAssigneeUsername() != null) {
            targets.add(task.getAssigneeUsername());
        }
        if (task.getCandidateUserIdsJson() != null) {
            // 候选用户名需要从 JSON 解析，催办主要是通知当前处理人，跳过空解析
        }
        if (targets.isEmpty()) {
            targets.add("候选组");
        }
        WorkflowTaskUrgeView view = WorkflowTaskUrgeView.from(entity, targets);
        int total = countUrges(task.getTenantId(), task.getId());
        auditEventPublisher.publish(PlatformAuditEvent.of("WORKFLOW_TASK_URGED", user.username(), user.tenantId(), Map.of(
                "taskId", task.getId(),
                "instanceId", task.getInstanceId(),
                "urgeId", entity.getId(),
                "totalUrgeCount", total
        )));
        return new WorkflowTaskUrgeResult(view, total, null);
    }

    public List<WorkflowTaskUrgeView> listUrges(Long taskId) {
        UserAccount user = currentUserService.requireCurrentUser();
        WfTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getId, taskId)
                .eq(WfTaskEntity::getTenantId, user.tenantId())
                .eq(WfTaskEntity::getDeleted, 0)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException("NOT_FOUND", "任务不存在");
        }
        List<WfTaskUrgeEntity> records = urgeMapper.selectList(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, user.tenantId())
                .eq(WfTaskUrgeEntity::getTaskId, taskId)
                .eq(WfTaskUrgeEntity::getDeleted, 0)
                .orderByDesc(WfTaskUrgeEntity::getUrgedAt)
                .orderByDesc(WfTaskUrgeEntity::getId));
        List<WorkflowTaskUrgeView> views = new ArrayList<>();
        for (WfTaskUrgeEntity entity : records) {
            Set<String> targets = new LinkedHashSet<>();
            if (task.getAssigneeUsername() != null) {
                targets.add(task.getAssigneeUsername());
            } else {
                targets.add("候选组");
            }
            views.add(WorkflowTaskUrgeView.from(entity, targets));
        }
        return views;
    }

    public int countUrges(String tenantId, Long taskId) {
        Long count = urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getTaskId, taskId)
                .eq(WfTaskUrgeEntity::getDeleted, 0));
        return count == null ? 0 : count.intValue();
    }

    public PageResult<WorkflowTaskUrgeView> listUrgesByInstance(Long instanceId, int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long total = urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, user.tenantId())
                .eq(WfTaskUrgeEntity::getInstanceId, instanceId)
                .eq(WfTaskUrgeEntity::getDeleted, 0));
        int offset = (safePage - 1) * safeSize;
        List<WfTaskUrgeEntity> records = urgeMapper.selectList(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, user.tenantId())
                .eq(WfTaskUrgeEntity::getInstanceId, instanceId)
                .eq(WfTaskUrgeEntity::getDeleted, 0)
                .orderByDesc(WfTaskUrgeEntity::getUrgedAt)
                .orderByDesc(WfTaskUrgeEntity::getId)
                .last("limit " + offset + "," + safeSize));
        List<WorkflowTaskUrgeView> views = new ArrayList<>();
        for (WfTaskUrgeEntity entity : records) {
            views.add(WorkflowTaskUrgeView.from(entity, Set.of("当前处理人")));
        }
        return PageResult.of(total, safePage, safeSize, views);
    }

    private boolean canUrge(WfTaskEntity task, UserAccount user) {
        if (user.permissions().contains("workflow:write")) {
            return true;
        }
        // 候选人或当前 assignee 允许自催；其他人需要 workflow:read 权限
        if (task.getAssigneeUserId() != null && Objects.equals(task.getAssigneeUserId(), user.id())) {
            return true;
        }
        Set<Long> candidateUserIds = parseLongSet(task.getCandidateUserIdsJson());
        if (candidateUserIds.contains(user.id())) {
            return true;
        }
        return user.permissions().contains("workflow:read");
    }

    private Set<Long> parseLongSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            Set<Long> values = objectMapper.readValue(json, LONG_SET_TYPE);
            return values == null ? Set.of() : values;
        } catch (Exception ex) {
            return Set.of();
        }
    }
}