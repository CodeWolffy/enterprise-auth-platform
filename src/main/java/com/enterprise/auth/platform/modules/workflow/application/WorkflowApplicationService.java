package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.common.web.PageResult;
import org.springframework.stereotype.Service;

/**
 * 工作流应用服务门面：保持既有调用方（Controller/测试）签名不变，
 * 将职责委托给流程定义、流程实例与流程任务三个应用服务。
 *
 * <p>事务边界位于各目标服务的公共入口方法上（跨 bean 调用走代理，事务生效）。
 */
@Service
public class WorkflowApplicationService {

    private final WorkflowDefinitionService definitionService;
    private final WorkflowInstanceService instanceService;
    private final WorkflowTaskService taskService;

    public WorkflowApplicationService(
            WorkflowDefinitionService definitionService,
            WorkflowInstanceService instanceService,
            WorkflowTaskService taskService
    ) {
        this.definitionService = definitionService;
        this.instanceService = instanceService;
        this.taskService = taskService;
    }

    // ---------- 流程定义 ----------

    public WorkflowDefinitionView createDefinition(WorkflowDefinitionCommand command) {
        return definitionService.createDefinition(command);
    }

    public WorkflowDefinitionView deployDefinition(Long definitionId) {
        return definitionService.deployDefinition(definitionId);
    }

    public WorkflowDefinitionView disableDefinition(Long definitionId) {
        return definitionService.disableDefinition(definitionId);
    }

    public WorkflowDefinitionView definition(Long definitionId) {
        return definitionService.definition(definitionId);
    }

    public PageResult<WorkflowDefinitionView> definitions(String status, int page, int size) {
        return definitionService.definitions(status, page, size);
    }

    // ---------- 流程实例 ----------

    public WorkflowStartResult startInstance(WorkflowStartCommand command) {
        return instanceService.startInstance(command);
    }

    public WorkflowInstanceView instance(Long instanceId) {
        return instanceService.instance(instanceId);
    }

    public PageResult<WorkflowInstanceView> myInstances(String status, int page, int size) {
        return instanceService.myInstances(status, page, size);
    }

    public WorkflowInstanceView withdrawInstance(Long instanceId) {
        return instanceService.withdrawInstance(instanceId);
    }

    public WorkflowInstanceView terminateInstance(Long instanceId, WorkflowTaskCommand command) {
        return instanceService.terminateInstance(instanceId, command);
    }

    // ---------- 流程任务 ----------

    public WorkflowActionResult approveTask(Long taskId, WorkflowTaskCommand command) {
        return taskService.approveTask(taskId, command);
    }

    public WorkflowActionResult rejectTask(Long taskId, WorkflowTaskCommand command) {
        return taskService.rejectTask(taskId, command);
    }

    public WorkflowActionResult transferTask(Long taskId, WorkflowTaskTransferCommand command) {
        return taskService.transferTask(taskId, command);
    }

    public PageResult<WorkflowTaskView> todoTasks(int page, int size) {
        return taskService.todoTasks(page, size);
    }

    public PageResult<WorkflowTaskView> todoTasks(int page, int size, Long taskId) {
        return taskService.todoTasks(page, size, taskId);
    }

    public PageResult<WorkflowTaskView> doneTasks(int page, int size) {
        return taskService.doneTasks(page, size);
    }
}
