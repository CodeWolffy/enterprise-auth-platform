package com.enterprise.auth.platform.modules.workflow.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinitionStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstance;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRepository;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskUrge;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessDefinitionEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessInstanceEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskUrgeEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessDefinitionMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessInstanceMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskCandidateRoleMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskCandidateUserMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskUrgeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class MybatisWorkflowRepository implements WorkflowRepository {

    private final WfProcessDefinitionMapper definitionMapper;
    private final WfProcessInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final WfTaskUrgeMapper urgeMapper;
    private final WorkflowCandidateLinkWriter candidateLinkWriter;
    private static final TypeReference<List<WorkflowStepDefinition>> STEP_LIST_TYPE = new TypeReference<>() { };
    private static final TypeReference<Set<Long>> LONG_SET_TYPE = new TypeReference<>() { };
    private static final TypeReference<Set<String>> STRING_SET_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public MybatisWorkflowRepository(
            WfProcessDefinitionMapper definitionMapper,
            WfProcessInstanceMapper instanceMapper,
            WfTaskMapper taskMapper,
            WfTaskUrgeMapper urgeMapper,
            WfTaskCandidateUserMapper candidateUserMapper,
            WfTaskCandidateRoleMapper candidateRoleMapper,
            ObjectMapper objectMapper
    ) {
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.urgeMapper = urgeMapper;
        this.candidateLinkWriter = new WorkflowCandidateLinkWriter(candidateUserMapper, candidateRoleMapper);
        this.objectMapper = objectMapper;
    }

    @Override
    public void insertDefinition(WorkflowDefinition definition) {
        WfProcessDefinitionEntity entity = toEntity(definition);
        definitionMapper.insert(entity);
        definition.setId(entity.getId());
        definition.setCreatedAt(entity.getCreatedAt());
        definition.setUpdatedAt(entity.getUpdatedAt());
    }

    @Override
    public void updateDefinition(WorkflowDefinition definition) {
        definitionMapper.updateById(toEntity(definition));
    }

    @Override
    public Optional<WorkflowDefinition> findDefinition(String tenantId, Long definitionId, boolean globalScope) {
        return Optional.ofNullable(definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                        .eq(!globalScope, WfProcessDefinitionEntity::getTenantId, tenantId)
                        .eq(WfProcessDefinitionEntity::getId, definitionId)
                        .eq(WfProcessDefinitionEntity::getDeleted, 0)
                        .last("limit 1")))
                .map(this::toDomain);
    }

    @Override
    public Optional<WorkflowDefinition> findLatestDeployedDefinition(String tenantId, String definitionKey) {
        return Optional.ofNullable(definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                        .eq(WfProcessDefinitionEntity::getTenantId, tenantId)
                        .eq(WfProcessDefinitionEntity::getDefinitionKey, definitionKey)
                        .eq(WfProcessDefinitionEntity::getStatus, WorkflowDefinitionStatus.DEPLOYED.name())
                        .eq(WfProcessDefinitionEntity::getDeleted, 0)
                        .orderByDesc(WfProcessDefinitionEntity::getVersion)
                        .last("limit 1")))
                .map(this::toDomain);
    }

    @Override
    public Optional<WorkflowDefinition> findLatestDefinition(String tenantId, String definitionKey) {
        return Optional.ofNullable(definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                        .eq(WfProcessDefinitionEntity::getTenantId, tenantId)
                        .eq(WfProcessDefinitionEntity::getDefinitionKey, definitionKey)
                        .eq(WfProcessDefinitionEntity::getDeleted, 0)
                        .orderByDesc(WfProcessDefinitionEntity::getVersion)
                        .last("limit 1")))
                .map(this::toDomain);
    }

    @Override
    public long countDefinitions(String tenantId, boolean globalScope, String status) {
        return definitionMapper.selectCount(definitionQuery(tenantId, globalScope, status));
    }

    @Override
    public List<WorkflowDefinition> findDefinitions(
            String tenantId, boolean globalScope, String status, int offset, int limit) {
        return definitionMapper.selectList(definitionQuery(tenantId, globalScope, status)
                        .orderByDesc(WfProcessDefinitionEntity::getUpdatedAt)
                        .orderByDesc(WfProcessDefinitionEntity::getId)
                        .last("limit " + offset + "," + limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insertInstance(WorkflowInstance instance) {
        WfProcessInstanceEntity entity = toEntity(instance);
        instanceMapper.insert(entity);
        instance.setId(entity.getId());
        instance.setCreatedAt(entity.getCreatedAt());
        instance.setUpdatedAt(entity.getUpdatedAt());
    }

    @Override
    public void updateInstance(WorkflowInstance instance) {
        instanceMapper.updateById(toEntity(instance));
    }

    @Override
    public Optional<WorkflowInstance> findInstance(String tenantId, Long instanceId) {
        return Optional.ofNullable(instanceMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                        .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                        .eq(WfProcessInstanceEntity::getId, instanceId)
                        .eq(WfProcessInstanceEntity::getDeleted, 0)
                        .last("limit 1")))
                .map(this::toDomain);
    }

    @Override
    public boolean existsBusinessKey(String tenantId, String businessKey) {
        return instanceMapper.selectCount(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getBusinessKey, businessKey)
                .eq(WfProcessInstanceEntity::getDeleted, 0)) > 0;
    }

    @Override
    public long countStartedInstances(String tenantId, Long starterUserId, String status) {
        return instanceMapper.selectCount(instanceQuery(tenantId, starterUserId, status));
    }

    @Override
    public List<WorkflowInstance> findStartedInstances(
            String tenantId, Long starterUserId, String status, int offset, int limit) {
        return instanceMapper.selectList(instanceQuery(tenantId, starterUserId, status)
                        .orderByDesc(WfProcessInstanceEntity::getStartedAt)
                        .orderByDesc(WfProcessInstanceEntity::getId)
                        .last("limit " + offset + "," + limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insertTask(WorkflowTask task) {
        WfTaskEntity entity = toEntity(task);
        taskMapper.insert(entity);
        task.setId(entity.getId());
        task.setCreatedAt(entity.getCreatedAt());
        task.setUpdatedAt(entity.getUpdatedAt());
        // expand-contract 双写：规范化候选关系，供 SQL 下推
        candidateLinkWriter.write(task);
    }

    @Override
    public Optional<WorkflowTask> findTask(String tenantId, Long taskId) {
        return Optional.ofNullable(taskMapper.selectOne(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .eq(WfTaskEntity::getId, taskId)
                        .eq(WfTaskEntity::getDeleted, 0)
                        .last("limit 1")))
                .map(this::toDomain);
    }

    @Override
    public Optional<WorkflowTask> findPendingTask(String tenantId, Long taskId) {
        return Optional.ofNullable(taskMapper.selectOne(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .eq(WfTaskEntity::getId, taskId)
                        .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                        .eq(WfTaskEntity::getDeleted, 0)
                        .last("limit 1")))
                .map(this::toDomain);
    }

    @Override
    public boolean completePendingTask(WorkflowTask task) {
        return taskMapper.update(null, new LambdaUpdateWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, task.getTenantId())
                .eq(WfTaskEntity::getId, task.getId())
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .set(WfTaskEntity::getStatus, task.getStatus().name())
                .set(WfTaskEntity::getAssigneeUserId, task.getAssigneeUserId())
                .set(WfTaskEntity::getAssigneeUsername, task.getAssigneeUsername())
                .set(WfTaskEntity::getComment, task.getComment())
                .set(WfTaskEntity::getCompletedAt, task.getCompletedAt())) > 0;
    }

    @Override
    public int cancelPendingTasks(
            String tenantId, Long instanceId, WorkflowTaskStatus status, String comment, Instant completedAt) {
        return taskMapper.update(null, new LambdaUpdateWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getInstanceId, instanceId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .set(WfTaskEntity::getStatus, status.name())
                .set(WfTaskEntity::getComment, comment)
                .set(WfTaskEntity::getCompletedAt, completedAt));
    }

    @Override
    public List<WorkflowTask> findPendingTasks(String tenantId, Long instanceId) {
        return taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .eq(WfTaskEntity::getInstanceId, instanceId)
                        .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                        .eq(WfTaskEntity::getDeleted, 0))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<WorkflowTask> findInstanceTasks(String tenantId, Long instanceId) {
        return taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .eq(WfTaskEntity::getInstanceId, instanceId)
                        .eq(WfTaskEntity::getDeleted, 0))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<WorkflowTask> findTodoCandidates(String tenantId, Long userId, Long taskId, int limit) {
        // 候选关系表 SQL 下推，避免全量 PENDING 再内存过滤
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        LambdaQueryWrapper<WfTaskEntity> query = new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .eq(taskId != null && taskId > 0, WfTaskEntity::getId, taskId)
                .and(wrapper -> wrapper
                        .eq(WfTaskEntity::getAssigneeUserId, userId)
                        .or(w -> w.isNull(WfTaskEntity::getAssigneeUserId)
                                .apply("EXISTS (SELECT 1 FROM wf_task_candidate_user cu WHERE cu.tenant_id = wf_task.tenant_id AND cu.task_id = wf_task.id AND cu.user_id = {0})", userId))
                        .or(w -> w.isNull(WfTaskEntity::getAssigneeUserId)
                                .apply("""
                                        EXISTS (
                                          SELECT 1 FROM wf_task_candidate_role cr
                                          INNER JOIN sys_role r ON r.tenant_id = cr.tenant_id AND r.role_code = cr.role_code AND r.deleted = 0
                                          INNER JOIN sys_user_role ur ON ur.tenant_id = r.tenant_id AND ur.role_id = r.id AND ur.user_id = {0}
                                          WHERE cr.tenant_id = wf_task.tenant_id AND cr.task_id = wf_task.id
                                        )
                                        """, userId))
                )
                .orderByAsc(WfTaskEntity::getCreatedAt)
                .orderByAsc(WfTaskEntity::getId)
                .last("limit " + safeLimit);
        return taskMapper.selectList(query).stream().map(this::toDomain).toList();
    }

    @Override
    public List<WorkflowTask> findDoneTasks(String tenantId, Long userId) {
        return findDoneTasks(tenantId, userId, 0, 100);
    }

    @Override
    public long countDoneTasks(String tenantId, Long userId) {
        return taskMapper.selectCount(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .ne(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getAssigneeUserId, userId)
                .eq(WfTaskEntity::getDeleted, 0));
    }

    @Override
    public List<WorkflowTask> findDoneTasks(String tenantId, Long userId, int offset, int limit) {
        int safeOffset = Math.max(offset, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .ne(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                        .eq(WfTaskEntity::getAssigneeUserId, userId)
                        .eq(WfTaskEntity::getDeleted, 0)
                        .orderByDesc(WfTaskEntity::getCompletedAt)
                        .orderByDesc(WfTaskEntity::getId)
                        .last("limit " + safeOffset + "," + safeLimit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insertUrge(WorkflowTaskUrge urge) {
        WfTaskUrgeEntity entity = toEntity(urge);
        urgeMapper.insert(entity);
        urge.setId(entity.getId());
        urge.setCreatedAt(entity.getCreatedAt());
        urge.setUpdatedAt(entity.getUpdatedAt());
    }

    @Override
    public long countUrges(String tenantId, Long taskId) {
        return urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getTaskId, taskId)
                .eq(WfTaskUrgeEntity::getDeleted, 0));
    }

    @Override
    public Map<Long, Long> countUrgesByTaskIds(String tenantId, Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        List<WfTaskUrgeEntity> rows = urgeMapper.selectList(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .select(WfTaskUrgeEntity::getTaskId)
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .in(WfTaskUrgeEntity::getTaskId, taskIds)
                .eq(WfTaskUrgeEntity::getDeleted, 0));
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (WfTaskUrgeEntity row : rows) {
            if (row.getTaskId() == null) {
                continue;
            }
            counts.merge(row.getTaskId(), 1L, Long::sum);
        }
        return counts;
    }

    @Override
    public List<WorkflowTaskUrge> findUrgesByTask(String tenantId, Long taskId) {
        return urgeMapper.selectList(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                        .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                        .eq(WfTaskUrgeEntity::getTaskId, taskId)
                        .eq(WfTaskUrgeEntity::getDeleted, 0)
                        .orderByDesc(WfTaskUrgeEntity::getUrgedAt)
                        .orderByDesc(WfTaskUrgeEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countUrgesByInstance(String tenantId, Long instanceId) {
        return urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getInstanceId, instanceId)
                .eq(WfTaskUrgeEntity::getDeleted, 0));
    }

    @Override
    public List<WorkflowTaskUrge> findUrgesByInstance(String tenantId, Long instanceId, int offset, int limit) {
        return urgeMapper.selectList(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                        .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                        .eq(WfTaskUrgeEntity::getInstanceId, instanceId)
                        .eq(WfTaskUrgeEntity::getDeleted, 0)
                        .orderByDesc(WfTaskUrgeEntity::getUrgedAt)
                        .orderByDesc(WfTaskUrgeEntity::getId)
                        .last("limit " + offset + "," + limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countUserUrgesSince(String tenantId, Long taskId, Long userId, Instant since) {
        return urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getTaskId, taskId)
                .eq(WfTaskUrgeEntity::getUrgedByUserId, userId)
                .eq(WfTaskUrgeEntity::getDeleted, 0)
                .ge(WfTaskUrgeEntity::getUrgedAt, since));
    }

    private LambdaQueryWrapper<WfProcessDefinitionEntity> definitionQuery(
            String tenantId, boolean globalScope, String status) {
        return new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(!globalScope, WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getDeleted, 0)
                .eq(StringUtils.hasText(status), WfProcessDefinitionEntity::getStatus,
                        StringUtils.hasText(status) ? status.trim().toUpperCase() : null);
    }

    private LambdaQueryWrapper<WfProcessInstanceEntity> instanceQuery(
            String tenantId, Long starterUserId, String status) {
        return new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getStarterUserId, starterUserId)
                .eq(WfProcessInstanceEntity::getDeleted, 0)
                .eq(StringUtils.hasText(status), WfProcessInstanceEntity::getStatus,
                        StringUtils.hasText(status) ? status.trim().toUpperCase() : null);
    }

    private WorkflowDefinition toDomain(WfProcessDefinitionEntity entity) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(entity.getId());
        definition.setTenantId(entity.getTenantId());
        definition.setDefinitionKey(entity.getDefinitionKey());
        definition.setDefinitionName(entity.getDefinitionName());
        definition.setVersion(entity.getVersion());
        definition.setStatus(WorkflowDefinitionStatus.valueOf(entity.getStatus()));
        definition.setSteps(read(entity.getStepsJson(), STEP_LIST_TYPE, List.of()));
        definition.setRemark(entity.getRemark());
        definition.setCreatedAt(entity.getCreatedAt());
        definition.setUpdatedAt(entity.getUpdatedAt());
        return definition;
    }

    private WfProcessDefinitionEntity toEntity(WorkflowDefinition definition) {
        WfProcessDefinitionEntity entity = new WfProcessDefinitionEntity();
        entity.setId(definition.getId());
        entity.setTenantId(definition.getTenantId());
        entity.setDefinitionKey(definition.getDefinitionKey());
        entity.setDefinitionName(definition.getDefinitionName());
        entity.setVersion(definition.getVersion());
        entity.setStatus(definition.getStatus().name());
        entity.setStepsJson(toJson(definition.getSteps()));
        entity.setRemark(definition.getRemark());
        entity.setCreatedAt(definition.getCreatedAt());
        entity.setUpdatedAt(definition.getUpdatedAt());
        return entity;
    }

    private WorkflowInstance toDomain(WfProcessInstanceEntity entity) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(entity.getId());
        instance.setTenantId(entity.getTenantId());
        instance.setDefinitionId(entity.getDefinitionId());
        instance.setDefinitionKey(entity.getDefinitionKey());
        instance.setDefinitionVersion(entity.getDefinitionVersion());
        instance.setBusinessKey(entity.getBusinessKey());
        instance.setTitle(entity.getTitle());
        instance.setStatus(WorkflowInstanceStatus.valueOf(entity.getStatus()));
        instance.setStarterUserId(entity.getStarterUserId());
        instance.setStarterUsername(entity.getStarterUsername());
        instance.setCurrentStepIndex(entity.getCurrentStepIndex());
        instance.setVariablesSnapshot(read(entity.getVariablesSnapshotJson(), MAP_TYPE, Map.of()));
        instance.setStartedAt(entity.getStartedAt());
        instance.setEndedAt(entity.getEndedAt());
        instance.setCreatedAt(entity.getCreatedAt());
        instance.setUpdatedAt(entity.getUpdatedAt());
        return instance;
    }

    private WfProcessInstanceEntity toEntity(WorkflowInstance instance) {
        WfProcessInstanceEntity entity = new WfProcessInstanceEntity();
        entity.setId(instance.getId());
        entity.setTenantId(instance.getTenantId());
        entity.setDefinitionId(instance.getDefinitionId());
        entity.setDefinitionKey(instance.getDefinitionKey());
        entity.setDefinitionVersion(instance.getDefinitionVersion());
        entity.setBusinessKey(instance.getBusinessKey());
        entity.setTitle(instance.getTitle());
        entity.setStatus(instance.getStatus().name());
        entity.setStarterUserId(instance.getStarterUserId());
        entity.setStarterUsername(instance.getStarterUsername());
        entity.setCurrentStepIndex(instance.getCurrentStepIndex());
        entity.setVariablesSnapshotJson(toJson(instance.getVariablesSnapshot()));
        entity.setStartedAt(instance.getStartedAt());
        entity.setEndedAt(instance.getEndedAt());
        entity.setCreatedAt(instance.getCreatedAt());
        entity.setUpdatedAt(instance.getUpdatedAt());
        return entity;
    }

    private WorkflowTask toDomain(WfTaskEntity entity) {
        WorkflowTask task = new WorkflowTask();
        task.setId(entity.getId());
        task.setTenantId(entity.getTenantId());
        task.setInstanceId(entity.getInstanceId());
        task.setDefinitionId(entity.getDefinitionId());
        task.setStepIndex(entity.getStepIndex());
        task.setStepName(entity.getStepName());
        task.setStatus(WorkflowTaskStatus.valueOf(entity.getStatus()));
        task.setCandidateUserIds(read(entity.getCandidateUserIdsJson(), LONG_SET_TYPE, Set.of()));
        task.setCandidateGroupCodes(read(entity.getCandidateGroupCodesJson(), STRING_SET_TYPE, Set.of()));
        task.setAssigneeUserId(entity.getAssigneeUserId());
        task.setAssigneeUsername(entity.getAssigneeUsername());
        task.setComment(entity.getComment());
        task.setCreatedAt(entity.getCreatedAt());
        task.setCompletedAt(entity.getCompletedAt());
        task.setUpdatedAt(entity.getUpdatedAt());
        return task;
    }

    private WfTaskEntity toEntity(WorkflowTask task) {
        WfTaskEntity entity = new WfTaskEntity();
        entity.setId(task.getId());
        entity.setTenantId(task.getTenantId());
        entity.setInstanceId(task.getInstanceId());
        entity.setDefinitionId(task.getDefinitionId());
        entity.setStepIndex(task.getStepIndex());
        entity.setStepName(task.getStepName());
        entity.setStatus(task.getStatus().name());
        entity.setCandidateUserIdsJson(toJson(task.getCandidateUserIds()));
        entity.setCandidateGroupCodesJson(toJson(task.getCandidateGroupCodes()));
        entity.setAssigneeUserId(task.getAssigneeUserId());
        entity.setAssigneeUsername(task.getAssigneeUsername());
        entity.setComment(task.getComment());
        entity.setCreatedAt(task.getCreatedAt());
        entity.setCompletedAt(task.getCompletedAt());
        entity.setUpdatedAt(task.getUpdatedAt());
        return entity;
    }

    private WorkflowTaskUrge toDomain(WfTaskUrgeEntity entity) {
        WorkflowTaskUrge urge = new WorkflowTaskUrge();
        urge.setId(entity.getId());
        urge.setTenantId(entity.getTenantId());
        urge.setTaskId(entity.getTaskId());
        urge.setInstanceId(entity.getInstanceId());
        urge.setUrgedByUserId(entity.getUrgedByUserId());
        urge.setUrgedByUsername(entity.getUrgedByUsername());
        urge.setComment(entity.getComment());
        urge.setUrgedAt(entity.getUrgedAt());
        urge.setCreatedAt(entity.getCreatedAt());
        urge.setUpdatedAt(entity.getUpdatedAt());
        return urge;
    }

    private WfTaskUrgeEntity toEntity(WorkflowTaskUrge urge) {
        WfTaskUrgeEntity entity = new WfTaskUrgeEntity();
        entity.setId(urge.getId());
        entity.setTenantId(urge.getTenantId());
        entity.setTaskId(urge.getTaskId());
        entity.setInstanceId(urge.getInstanceId());
        entity.setUrgedByUserId(urge.getUrgedByUserId());
        entity.setUrgedByUsername(urge.getUrgedByUsername());
        entity.setComment(urge.getComment());
        entity.setUrgedAt(urge.getUrgedAt());
        entity.setCreatedAt(urge.getCreatedAt());
        entity.setUpdatedAt(urge.getUpdatedAt());
        return entity;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("JSON_SERIALIZE_FAILED", "工作流持久化数据序列化失败");
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            T value = objectMapper.readValue(json, type);
            return value == null ? fallback : value;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("JSON_PARSE_FAILED", "工作流持久化数据解析失败");
        }
    }
}
