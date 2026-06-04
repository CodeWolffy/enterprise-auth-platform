package com.enterprise.auth.platform.modules.workflow.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.audit.AuditEventPublisher;
import com.enterprise.auth.platform.common.audit.PlatformAuditEvent;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinitionStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessDefinitionEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessInstanceEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessDefinitionMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessInstanceMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkflowApplicationService {

    private static final TypeReference<List<WorkflowStepDefinition>> STEP_LIST_TYPE = new TypeReference<>() { };
    private static final TypeReference<Set<Long>> LONG_SET_TYPE = new TypeReference<>() { };
    private static final TypeReference<Set<String>> STRING_SET_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final WfProcessDefinitionMapper definitionMapper;
    private final WfProcessInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final CurrentUserService currentUserService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public WorkflowApplicationService(
            WfProcessDefinitionMapper definitionMapper,
            WfProcessInstanceMapper instanceMapper,
            WfTaskMapper taskMapper,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            CurrentUserService currentUserService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.currentUserService = currentUserService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowDefinitionView createDefinition(WorkflowDefinitionCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        List<WorkflowStepDefinition> steps = normalizeSteps(command.steps());
        validateCandidates(tenantId, steps);
        int version = nextDefinitionVersion(tenantId, command.definitionKey());

        WfProcessDefinitionEntity entity = new WfProcessDefinitionEntity();
        entity.setTenantId(tenantId);
        entity.setDefinitionKey(command.definitionKey().trim());
        entity.setDefinitionName(command.definitionName().trim());
        entity.setVersion(version);
        entity.setStatus(WorkflowDefinitionStatus.DRAFT.name());
        entity.setStepsJson(toJson(steps));
        entity.setRemark(normalizeText(command.remark()));
        definitionMapper.insert(entity);
        publish("WORKFLOW_DEFINITION_CREATED", user, tenantId, Map.of(
                "definitionId", entity.getId(),
                "definitionKey", entity.getDefinitionKey(),
                "version", entity.getVersion()
        ));
        return toDefinitionView(entity);
    }

    @Transactional
    public WorkflowDefinitionView deployDefinition(Long definitionId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfProcessDefinitionEntity entity = requireDefinition(tenantId, definitionId);
        if (WorkflowDefinitionStatus.DISABLED.name().equals(entity.getStatus())) {
            throw new BusinessException("已停用流程定义不能重新部署，请创建新版本");
        }
        entity.setStatus(WorkflowDefinitionStatus.DEPLOYED.name());
        definitionMapper.updateById(entity);
        publish("WORKFLOW_DEFINITION_DEPLOYED", user, tenantId, Map.of(
                "definitionId", entity.getId(),
                "definitionKey", entity.getDefinitionKey(),
                "version", entity.getVersion()
        ));
        return toDefinitionView(entity);
    }

    @Transactional
    public WorkflowDefinitionView disableDefinition(Long definitionId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfProcessDefinitionEntity entity = requireDefinition(tenantId, definitionId);
        entity.setStatus(WorkflowDefinitionStatus.DISABLED.name());
        definitionMapper.updateById(entity);
        publish("WORKFLOW_DEFINITION_DISABLED", user, tenantId, Map.of(
                "definitionId", entity.getId(),
                "definitionKey", entity.getDefinitionKey(),
                "version", entity.getVersion()
        ));
        return toDefinitionView(entity);
    }

    public WorkflowDefinitionView definition(Long definitionId) {
        UserAccount user = currentUserService.requireCurrentUser();
        return toDefinitionView(requireDefinition(currentTenantId(user), definitionId));
    }

    public PageResult<WorkflowDefinitionView> definitions(String status, int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = normalizeSize(size);
        LambdaQueryWrapper<WfProcessDefinitionEntity> wrapper = new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getDeleted, 0);
        if (StringUtils.hasText(status)) {
            wrapper.eq(WfProcessDefinitionEntity::getStatus, status.trim().toUpperCase());
        }
        long total = definitionMapper.selectCount(wrapper);
        int offset = (normalizedPage - 1) * normalizedSize;
        wrapper.orderByDesc(WfProcessDefinitionEntity::getUpdatedAt)
                .orderByDesc(WfProcessDefinitionEntity::getId)
                .last("limit " + offset + "," + normalizedSize);
        List<WorkflowDefinitionView> records = definitionMapper.selectList(wrapper).stream()
                .map(this::toDefinitionView)
                .toList();
        return PageResult.of(total, normalizedPage, normalizedSize, records);
    }

    @Transactional
    public WorkflowStartResult startInstance(WorkflowStartCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfProcessDefinitionEntity definition = requireLatestDeployedDefinition(tenantId, command.definitionKey());
        List<WorkflowStepDefinition> steps = readSteps(definition.getStepsJson());
        if (steps.isEmpty()) {
            throw new BusinessException("流程定义没有审批步骤");
        }
        if (existsBusinessKey(tenantId, command.businessKey())) {
            throw new BusinessException("CONFLICT", "业务单据已存在流程实例");
        }

        WfProcessInstanceEntity instance = new WfProcessInstanceEntity();
        instance.setTenantId(tenantId);
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionKey(definition.getDefinitionKey());
        instance.setDefinitionVersion(definition.getVersion());
        instance.setBusinessKey(command.businessKey().trim());
        instance.setTitle(command.title().trim());
        instance.setStatus(WorkflowInstanceStatus.RUNNING.name());
        instance.setStarterUserId(user.id());
        instance.setStarterUsername(user.username());
        instance.setCurrentStepIndex(0);
        instance.setVariablesSnapshotJson(toJson(snapshotVariables(command.variables())));
        instance.setStartedAt(TimeSupport.utcNowDateTime());
        instanceMapper.insert(instance);

        WfTaskEntity task = createPendingTask(tenantId, instance, definition, steps.get(0), 0);
        publish("WORKFLOW_INSTANCE_STARTED", user, tenantId, Map.of(
                "instanceId", instance.getId(),
                "definitionKey", definition.getDefinitionKey(),
                "definitionVersion", definition.getVersion(),
                "businessKey", instance.getBusinessKey()
        ));
        return new WorkflowStartResult(toInstanceView(instance), toTaskView(task, user));
    }

    public WorkflowInstanceView instance(Long instanceId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfProcessInstanceEntity instance = requireInstance(tenantId, instanceId);
        if (!canViewInstance(instance, user)) {
            throw new BusinessException("ACCESS_DENIED", "无权查看该流程实例");
        }
        return toInstanceView(instance);
    }

    public PageResult<WorkflowInstanceView> myInstances(String status, int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = normalizeSize(size);
        LambdaQueryWrapper<WfProcessInstanceEntity> wrapper = new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getStarterUserId, user.id())
                .eq(WfProcessInstanceEntity::getDeleted, 0);
        if (StringUtils.hasText(status)) {
            wrapper.eq(WfProcessInstanceEntity::getStatus, status.trim().toUpperCase());
        }
        long total = instanceMapper.selectCount(wrapper);
        int offset = (normalizedPage - 1) * normalizedSize;
        wrapper.orderByDesc(WfProcessInstanceEntity::getStartedAt)
                .orderByDesc(WfProcessInstanceEntity::getId)
                .last("limit " + offset + "," + normalizedSize);
        List<WorkflowInstanceView> records = instanceMapper.selectList(wrapper).stream()
                .map(this::toInstanceView)
                .toList();
        return PageResult.of(total, normalizedPage, normalizedSize, records);
    }

    @Transactional
    public WorkflowActionResult approveTask(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfTaskEntity task = requirePendingTask(tenantId, taskId);
        WfProcessInstanceEntity instance = requireRunningInstance(tenantId, task.getInstanceId());
        ensureActionable(task, user);
        WfProcessDefinitionEntity definition = requireDefinition(tenantId, task.getDefinitionId());
        List<WorkflowStepDefinition> steps = readSteps(definition.getStepsJson());

        task.setStatus(WorkflowTaskStatus.APPROVED.name());
        task.setAssigneeUserId(user.id());
        task.setAssigneeUsername(user.username());
        task.setComment(normalizeText(command.comment()));
        task.setCompletedAt(TimeSupport.utcNowDateTime());
        taskMapper.updateById(task);

        WorkflowTaskView nextTask = null;
        int nextStepIndex = task.getStepIndex() + 1;
        if (nextStepIndex >= steps.size()) {
            instance.setStatus(WorkflowInstanceStatus.APPROVED.name());
            instance.setEndedAt(TimeSupport.utcNowDateTime());
        } else {
            instance.setCurrentStepIndex(nextStepIndex);
            WfTaskEntity created = createPendingTask(tenantId, instance, definition, steps.get(nextStepIndex), nextStepIndex);
            nextTask = toTaskView(created, user);
        }
        instanceMapper.updateById(instance);
        publish("WORKFLOW_TASK_APPROVED", user, tenantId, Map.of(
                "instanceId", instance.getId(),
                "taskId", task.getId(),
                "stepIndex", task.getStepIndex(),
                "ended", WorkflowInstanceStatus.APPROVED.name().equals(instance.getStatus())
        ));
        return new WorkflowActionResult(toInstanceView(instance), nextTask);
    }

    @Transactional
    public WorkflowActionResult rejectTask(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfTaskEntity task = requirePendingTask(tenantId, taskId);
        WfProcessInstanceEntity instance = requireRunningInstance(tenantId, task.getInstanceId());
        ensureActionable(task, user);

        task.setStatus(WorkflowTaskStatus.REJECTED.name());
        task.setAssigneeUserId(user.id());
        task.setAssigneeUsername(user.username());
        task.setComment(normalizeText(command.comment()));
        task.setCompletedAt(TimeSupport.utcNowDateTime());
        taskMapper.updateById(task);

        instance.setStatus(WorkflowInstanceStatus.REJECTED.name());
        instance.setEndedAt(TimeSupport.utcNowDateTime());
        instanceMapper.updateById(instance);
        publish("WORKFLOW_TASK_REJECTED", user, tenantId, Map.of(
                "instanceId", instance.getId(),
                "taskId", task.getId(),
                "stepIndex", task.getStepIndex()
        ));
        return new WorkflowActionResult(toInstanceView(instance), null);
    }

    @Transactional
    public WorkflowActionResult transferTask(Long taskId, WorkflowTaskTransferCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfTaskEntity task = requirePendingTask(tenantId, taskId);
        WfProcessInstanceEntity instance = requireRunningInstance(tenantId, task.getInstanceId());
        ensureActionable(task, user);
        SysUserEntity targetUser = requireEnabledUser(tenantId, command.targetUserId());
        if (Objects.equals(targetUser.getId(), user.id())) {
            throw new BusinessException("不能转签给自己");
        }

        LocalDateTime now = TimeSupport.utcNowDateTime();
        task.setStatus(WorkflowTaskStatus.TRANSFERRED.name());
        task.setAssigneeUserId(user.id());
        task.setAssigneeUsername(user.username());
        task.setComment(normalizeText(command.comment()));
        task.setCompletedAt(now);
        taskMapper.updateById(task);

        WfTaskEntity transferredTask = new WfTaskEntity();
        transferredTask.setTenantId(tenantId);
        transferredTask.setInstanceId(task.getInstanceId());
        transferredTask.setDefinitionId(task.getDefinitionId());
        transferredTask.setStepIndex(task.getStepIndex());
        transferredTask.setStepName(task.getStepName());
        transferredTask.setStatus(WorkflowTaskStatus.PENDING.name());
        transferredTask.setCandidateUserIdsJson(toJson(Set.of(targetUser.getId())));
        transferredTask.setCandidateGroupCodesJson(toJson(Set.of()));
        transferredTask.setAssigneeUserId(targetUser.getId());
        transferredTask.setAssigneeUsername(targetUser.getUsername());
        transferredTask.setComment("由 " + user.username() + " 转签");
        taskMapper.insert(transferredTask);

        publish("WORKFLOW_TASK_TRANSFERRED", user, tenantId, Map.of(
                "instanceId", instance.getId(),
                "taskId", task.getId(),
                "newTaskId", transferredTask.getId(),
                "stepIndex", task.getStepIndex(),
                "targetUserId", targetUser.getId(),
                "targetUsername", targetUser.getUsername()
        ));
        return new WorkflowActionResult(toInstanceView(instance), toTaskView(transferredTask, user));
    }

    @Transactional
    public WorkflowInstanceView withdrawInstance(Long instanceId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfProcessInstanceEntity instance = requireRunningInstance(tenantId, instanceId);
        if (!Objects.equals(instance.getStarterUserId(), user.id())) {
            throw new BusinessException("ACCESS_DENIED", "只有发起人可以撤回流程");
        }
        cancelPendingTasks(tenantId, instance.getId(), WorkflowTaskStatus.CANCELLED, "发起人撤回");
        instance.setStatus(WorkflowInstanceStatus.WITHDRAWN.name());
        instance.setEndedAt(TimeSupport.utcNowDateTime());
        instanceMapper.updateById(instance);
        publish("WORKFLOW_INSTANCE_WITHDRAWN", user, tenantId, Map.of("instanceId", instance.getId()));
        return toInstanceView(instance);
    }

    @Transactional
    public WorkflowInstanceView terminateInstance(Long instanceId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfProcessInstanceEntity instance = requireRunningInstance(tenantId, instanceId);
        cancelPendingTasks(tenantId, instance.getId(), WorkflowTaskStatus.CANCELLED, normalizeText(command.comment()));
        instance.setStatus(WorkflowInstanceStatus.TERMINATED.name());
        instance.setEndedAt(TimeSupport.utcNowDateTime());
        instanceMapper.updateById(instance);
        publish("WORKFLOW_INSTANCE_TERMINATED", user, tenantId, Map.of("instanceId", instance.getId()));
        return toInstanceView(instance);
    }

    public PageResult<WorkflowTaskView> todoTasks(int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        List<WorkflowTaskView> filtered = taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                        .eq(WfTaskEntity::getDeleted, 0)
                        .orderByAsc(WfTaskEntity::getCreatedAt)
                        .orderByAsc(WfTaskEntity::getId))
                .stream()
                .filter(task -> isActionable(task, user))
                .map(task -> toTaskView(task, user))
                .toList();
        return page(filtered, page, size);
    }

    public PageResult<WorkflowTaskView> doneTasks(int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        List<WorkflowTaskView> filtered = taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .ne(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                        .eq(WfTaskEntity::getAssigneeUserId, user.id())
                        .eq(WfTaskEntity::getDeleted, 0)
                        .orderByDesc(WfTaskEntity::getCompletedAt)
                        .orderByDesc(WfTaskEntity::getId))
                .stream()
                .map(task -> toTaskView(task, user))
                .toList();
        return page(filtered, page, size);
    }

    private WfTaskEntity createPendingTask(
            String tenantId,
            WfProcessInstanceEntity instance,
            WfProcessDefinitionEntity definition,
            WorkflowStepDefinition step,
            int stepIndex
    ) {
        WfTaskEntity task = new WfTaskEntity();
        task.setTenantId(tenantId);
        task.setInstanceId(instance.getId());
        task.setDefinitionId(definition.getId());
        task.setStepIndex(stepIndex);
        task.setStepName(step.name());
        task.setStatus(WorkflowTaskStatus.PENDING.name());
        task.setCandidateUserIdsJson(toJson(step.candidateUserIds()));
        task.setCandidateGroupCodesJson(toJson(step.candidateGroupCodes()));
        taskMapper.insert(task);
        return task;
    }

    private void cancelPendingTasks(String tenantId, Long instanceId, WorkflowTaskStatus status, String comment) {
        List<WfTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getInstanceId, instanceId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0));
        LocalDateTime now = TimeSupport.utcNowDateTime();
        for (WfTaskEntity task : tasks) {
            task.setStatus(status.name());
            task.setComment(comment);
            task.setCompletedAt(now);
            taskMapper.updateById(task);
        }
    }

    private WfProcessDefinitionEntity requireDefinition(String tenantId, Long definitionId) {
        WfProcessDefinitionEntity entity = definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getId, definitionId)
                .eq(WfProcessDefinitionEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "流程定义不存在");
        }
        return entity;
    }

    private WfProcessDefinitionEntity requireLatestDeployedDefinition(String tenantId, String definitionKey) {
        if (!StringUtils.hasText(definitionKey)) {
            throw new BusinessException("流程定义键不能为空");
        }
        WfProcessDefinitionEntity entity = definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getDefinitionKey, definitionKey.trim())
                .eq(WfProcessDefinitionEntity::getStatus, WorkflowDefinitionStatus.DEPLOYED.name())
                .eq(WfProcessDefinitionEntity::getDeleted, 0)
                .orderByDesc(WfProcessDefinitionEntity::getVersion)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "可发起的流程定义不存在");
        }
        return entity;
    }

    private WfProcessInstanceEntity requireInstance(String tenantId, Long instanceId) {
        WfProcessInstanceEntity entity = instanceMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getId, instanceId)
                .eq(WfProcessInstanceEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "流程实例不存在");
        }
        return entity;
    }

    private WfProcessInstanceEntity requireRunningInstance(String tenantId, Long instanceId) {
        WfProcessInstanceEntity instance = requireInstance(tenantId, instanceId);
        if (!WorkflowInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            throw new BusinessException("流程实例不是运行中状态");
        }
        return instance;
    }

    private WfTaskEntity requirePendingTask(String tenantId, Long taskId) {
        WfTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getId, taskId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException("NOT_FOUND", "待办任务不存在");
        }
        return task;
    }

    private SysUserEntity requireEnabledUser(String tenantId, Long userId) {
        if (userId == null) {
            throw new BusinessException("目标用户不能为空");
        }
        SysUserEntity user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getEnabled, 1)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (user == null) {
            throw new BusinessException("NOT_FOUND", "目标用户不存在或已禁用");
        }
        return user;
    }

    private boolean canViewInstance(WfProcessInstanceEntity instance, UserAccount user) {
        if (Objects.equals(instance.getStarterUserId(), user.id())) {
            return true;
        }
        if (user.permissions().contains("workflow:read")) {
            return true;
        }
        return taskMapper.selectCount(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, instance.getTenantId())
                .eq(WfTaskEntity::getInstanceId, instance.getId())
                .eq(WfTaskEntity::getAssigneeUserId, user.id())
                .eq(WfTaskEntity::getDeleted, 0)) > 0;
    }

    private void ensureActionable(WfTaskEntity task, UserAccount user) {
        if (!isActionable(task, user)) {
            throw new BusinessException("ACCESS_DENIED", "当前用户不是该任务候选处理人");
        }
    }

    private boolean isActionable(WfTaskEntity task, UserAccount user) {
        if (task.getAssigneeUserId() != null) {
            return Objects.equals(task.getAssigneeUserId(), user.id());
        }
        Set<Long> candidateUserIds = readLongSet(task.getCandidateUserIdsJson());
        if (candidateUserIds.contains(user.id())) {
            return true;
        }
        Set<String> candidateGroupCodes = readStringSet(task.getCandidateGroupCodesJson());
        return user.roles().stream().anyMatch(candidateGroupCodes::contains);
    }

    private boolean existsBusinessKey(String tenantId, String businessKey) {
        if (!StringUtils.hasText(businessKey)) {
            throw new BusinessException("业务键不能为空");
        }
        return instanceMapper.selectCount(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getBusinessKey, businessKey.trim())
                .eq(WfProcessInstanceEntity::getDeleted, 0)) > 0;
    }

    private int nextDefinitionVersion(String tenantId, String definitionKey) {
        if (!StringUtils.hasText(definitionKey)) {
            throw new BusinessException("流程定义键不能为空");
        }
        WfProcessDefinitionEntity latest = definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getDefinitionKey, definitionKey.trim())
                .eq(WfProcessDefinitionEntity::getDeleted, 0)
                .orderByDesc(WfProcessDefinitionEntity::getVersion)
                .last("limit 1"));
        return latest == null ? 1 : latest.getVersion() + 1;
    }

    private List<WorkflowStepDefinition> normalizeSteps(List<WorkflowStepDefinition> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new BusinessException("流程至少需要一个审批步骤");
        }
        List<WorkflowStepDefinition> normalized = new ArrayList<>();
        for (WorkflowStepDefinition step : steps) {
            if (step == null || !StringUtils.hasText(step.name())) {
                throw new BusinessException("审批步骤名称不能为空");
            }
            Set<Long> userIds = step.candidateUserIds().stream()
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<String> groupCodes = step.candidateGroupCodes().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (userIds.isEmpty() && groupCodes.isEmpty()) {
                throw new BusinessException("审批步骤至少需要一个候选人或候选组");
            }
            normalized.add(new WorkflowStepDefinition(step.name().trim(), userIds, groupCodes));
        }
        return normalized;
    }

    private void validateCandidates(String tenantId, List<WorkflowStepDefinition> steps) {
        Set<Long> userIds = new LinkedHashSet<>();
        Set<String> roleCodes = new LinkedHashSet<>();
        for (WorkflowStepDefinition step : steps) {
            userIds.addAll(step.candidateUserIds());
            roleCodes.addAll(step.candidateGroupCodes());
        }
        if (!userIds.isEmpty()) {
            long validUsers = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                    .eq(SysUserEntity::getTenantId, tenantId)
                    .eq(SysUserEntity::getDeleted, 0)
                    .in(SysUserEntity::getId, userIds));
            if (validUsers != userIds.size()) {
                throw new BusinessException("存在无效的候选人");
            }
        }
        if (!roleCodes.isEmpty()) {
            long validRoles = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRoleEntity>()
                    .eq(SysRoleEntity::getTenantId, tenantId)
                    .eq(SysRoleEntity::getDeleted, 0)
                    .in(SysRoleEntity::getRoleCode, roleCodes));
            if (validRoles != roleCodes.size()) {
                throw new BusinessException("存在无效的候选组");
            }
        }
    }

    private Map<String, Object> snapshotVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        variables.forEach((key, value) -> {
            if (StringUtils.hasText(key)) {
                snapshot.put(key.trim(), value);
            }
        });
        return snapshot;
    }

    private WorkflowDefinitionView toDefinitionView(WfProcessDefinitionEntity entity) {
        List<WorkflowStepDefinition> steps = readSteps(entity.getStepsJson());
        List<WorkflowStepView> stepViews = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            WorkflowStepDefinition step = steps.get(i);
            stepViews.add(new WorkflowStepView(i, step.name(), step.candidateUserIds(), step.candidateGroupCodes()));
        }
        return new WorkflowDefinitionView(
                entity.getId(),
                entity.getTenantId(),
                entity.getDefinitionKey(),
                entity.getDefinitionName(),
                entity.getVersion(),
                entity.getStatus(),
                stepViews,
                entity.getRemark(),
                TimeSupport.toEpochMilli(entity.getCreatedAt()),
                TimeSupport.toEpochMilli(entity.getUpdatedAt())
        );
    }

    private WorkflowInstanceView toInstanceView(WfProcessInstanceEntity entity) {
        return new WorkflowInstanceView(
                entity.getId(),
                entity.getTenantId(),
                entity.getDefinitionId(),
                entity.getDefinitionKey(),
                entity.getDefinitionVersion(),
                entity.getBusinessKey(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getStarterUserId(),
                entity.getStarterUsername(),
                entity.getCurrentStepIndex(),
                readMap(entity.getVariablesSnapshotJson()),
                TimeSupport.toEpochMilli(entity.getStartedAt()),
                TimeSupport.toEpochMilli(entity.getEndedAt())
        );
    }

    private WorkflowTaskView toTaskView(WfTaskEntity entity, UserAccount user) {
        return new WorkflowTaskView(
                entity.getId(),
                entity.getTenantId(),
                entity.getInstanceId(),
                entity.getDefinitionId(),
                entity.getStepIndex(),
                entity.getStepName(),
                entity.getStatus(),
                readLongSet(entity.getCandidateUserIdsJson()),
                readStringSet(entity.getCandidateGroupCodesJson()),
                entity.getAssigneeUserId(),
                entity.getAssigneeUsername(),
                entity.getComment(),
                TimeSupport.toEpochMilli(entity.getCreatedAt()),
                TimeSupport.toEpochMilli(entity.getCompletedAt()),
                WorkflowTaskStatus.PENDING.name().equals(entity.getStatus()) && isActionable(entity, user)
        );
    }

    private <T> PageResult<T> page(List<T> items, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = normalizeSize(size);
        int from = Math.min((normalizedPage - 1) * normalizedSize, items.size());
        int to = Math.min(from + normalizedSize, items.size());
        return PageResult.of(items.size(), normalizedPage, normalizedSize, items.subList(from, to));
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private String currentTenantId(UserAccount user) {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : user.tenantId();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_SERIALIZE_FAILED", "数据序列化失败");
        }
    }

    private List<WorkflowStepDefinition> readSteps(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<WorkflowStepDefinition> steps = objectMapper.readValue(json, STEP_LIST_TYPE);
            return steps == null ? List.of() : steps;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_PARSE_FAILED", "流程步骤解析失败");
        }
    }

    private Set<Long> readLongSet(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            Set<Long> values = objectMapper.readValue(json, LONG_SET_TYPE);
            return values == null ? Set.of() : values;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_PARSE_FAILED", "候选人解析失败");
        }
    }

    private Set<String> readStringSet(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            Set<String> values = objectMapper.readValue(json, STRING_SET_TYPE);
            return values == null ? Set.of() : values;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_PARSE_FAILED", "候选组解析失败");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(json, MAP_TYPE);
            return value == null ? Map.of() : value;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_PARSE_FAILED", "变量快照解析失败");
        }
    }

    private void publish(String eventType, UserAccount user, String tenantId, Map<String, Object> details) {
        auditEventPublisher.publish(PlatformAuditEvent.of(eventType, user.username(), tenantId, details));
    }
}