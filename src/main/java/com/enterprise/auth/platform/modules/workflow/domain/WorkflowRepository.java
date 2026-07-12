package com.enterprise.auth.platform.modules.workflow.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WorkflowRepository {

    void insertDefinition(WorkflowDefinition definition);

    void updateDefinition(WorkflowDefinition definition);

    Optional<WorkflowDefinition> findDefinition(String tenantId, Long definitionId, boolean globalScope);

    Optional<WorkflowDefinition> findLatestDeployedDefinition(String tenantId, String definitionKey);

    Optional<WorkflowDefinition> findLatestDefinition(String tenantId, String definitionKey);

    long countDefinitions(String tenantId, boolean globalScope, String status);

    List<WorkflowDefinition> findDefinitions(String tenantId, boolean globalScope, String status, int offset, int limit);

    void insertInstance(WorkflowInstance instance);

    void updateInstance(WorkflowInstance instance);

    Optional<WorkflowInstance> findInstance(String tenantId, Long instanceId);

    boolean existsBusinessKey(String tenantId, String businessKey);

    long countStartedInstances(String tenantId, Long starterUserId, String status);

    List<WorkflowInstance> findStartedInstances(
            String tenantId, Long starterUserId, String status, int offset, int limit);

    void insertTask(WorkflowTask task);

    Optional<WorkflowTask> findTask(String tenantId, Long taskId);

    Optional<WorkflowTask> findPendingTask(String tenantId, Long taskId);

    boolean completePendingTask(WorkflowTask task);

    int cancelPendingTasks(
            String tenantId, Long instanceId, WorkflowTaskStatus status, String comment, Instant completedAt);

    List<WorkflowTask> findPendingTasks(String tenantId, Long instanceId);

    List<WorkflowTask> findInstanceTasks(String tenantId, Long instanceId);

    List<WorkflowTask> findTodoCandidates(String tenantId, Long userId, Long taskId, int limit);

    List<WorkflowTask> findDoneTasks(String tenantId, Long userId);

    long countDoneTasks(String tenantId, Long userId);

    List<WorkflowTask> findDoneTasks(String tenantId, Long userId, int offset, int limit);

    void insertUrge(WorkflowTaskUrge urge);

    long countUrges(String tenantId, Long taskId);

    /** 按 taskIds 批量统计催办数，避免 N 次 COUNT。 */
    Map<Long, Long> countUrgesByTaskIds(String tenantId, Collection<Long> taskIds);

    List<WorkflowTaskUrge> findUrgesByTask(String tenantId, Long taskId);

    long countUrgesByInstance(String tenantId, Long instanceId);

    List<WorkflowTaskUrge> findUrgesByInstance(String tenantId, Long instanceId, int offset, int limit);

    long countUserUrgesSince(String tenantId, Long taskId, Long userId, Instant since);
}
