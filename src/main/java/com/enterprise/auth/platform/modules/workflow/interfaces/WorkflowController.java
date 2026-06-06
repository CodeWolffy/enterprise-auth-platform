package com.enterprise.auth.platform.modules.workflow.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowActionResult;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowApplicationService;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowDefinitionView;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowInstanceView;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowStartResult;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskUrgeResult;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskUrgeView;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "工作流 PoC")
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowApplicationService workflowApplicationService;
    private final com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskUrgeService urgeService;

    public WorkflowController(WorkflowApplicationService workflowApplicationService,
                              com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskUrgeService urgeService) {
        this.workflowApplicationService = workflowApplicationService;
        this.urgeService = urgeService;
    }

    @Operation(summary = "创建流程定义草稿")
    @PostMapping("/process-definitions")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowDefinitionView> createDefinition(@Valid @RequestBody WorkflowDefinitionRequest request) {
        return ApiResponse.ok(workflowApplicationService.createDefinition(request.toCommand()));
    }

    @Operation(summary = "分页查询流程定义")
    @GetMapping("/process-definitions")
    @SaCheckPermission(PermissionCodes.WORKFLOW_READ)
    public ApiResponse<PageResult<WorkflowDefinitionView>> definitions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(workflowApplicationService.definitions(status, page, size));
    }

    @Operation(summary = "查询流程定义详情")
    @GetMapping("/process-definitions/{definitionId}")
    @SaCheckPermission(PermissionCodes.WORKFLOW_READ)
    public ApiResponse<WorkflowDefinitionView> definition(@Parameter(description = "流程定义 ID") @PathVariable Long definitionId) {
        return ApiResponse.ok(workflowApplicationService.definition(definitionId));
    }

    @Operation(summary = "部署流程定义")
    @PutMapping("/process-definitions/{definitionId}/deploy")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowDefinitionView> deployDefinition(@Parameter(description = "流程定义 ID") @PathVariable Long definitionId) {
        return ApiResponse.ok(workflowApplicationService.deployDefinition(definitionId));
    }

    @Operation(summary = "停用流程定义")
    @PutMapping("/process-definitions/{definitionId}/disable")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowDefinitionView> disableDefinition(@Parameter(description = "流程定义 ID") @PathVariable Long definitionId) {
        return ApiResponse.ok(workflowApplicationService.disableDefinition(definitionId));
    }

    @Operation(summary = "发起流程实例")
    @PostMapping("/instances")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowStartResult> startInstance(@Valid @RequestBody WorkflowStartRequest request) {
        return ApiResponse.ok(workflowApplicationService.startInstance(request.toCommand()));
    }

    @Operation(summary = "查询我的发起")
    @GetMapping("/instances/my")
    @SaCheckPermission(PermissionCodes.WORKFLOW_READ)
    public ApiResponse<PageResult<WorkflowInstanceView>> myInstances(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(workflowApplicationService.myInstances(status, page, size));
    }

    @Operation(summary = "查询流程实例详情")
    @GetMapping("/instances/{instanceId}")
    @SaCheckPermission(PermissionCodes.WORKFLOW_READ)
    public ApiResponse<WorkflowInstanceView> instance(@Parameter(description = "流程实例 ID") @PathVariable Long instanceId) {
        return ApiResponse.ok(workflowApplicationService.instance(instanceId));
    }

    @Operation(summary = "撤回流程实例")
    @PutMapping("/instances/{instanceId}/withdraw")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowInstanceView> withdrawInstance(@Parameter(description = "流程实例 ID") @PathVariable Long instanceId) {
        return ApiResponse.ok(workflowApplicationService.withdrawInstance(instanceId));
    }

    @Operation(summary = "终止流程实例")
    @PutMapping("/instances/{instanceId}/terminate")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowInstanceView> terminateInstance(
            @Parameter(description = "流程实例 ID") @PathVariable Long instanceId,
            @Valid @RequestBody(required = false) WorkflowTaskActionRequest request
    ) {
        WorkflowTaskActionRequest safeRequest = request == null ? new WorkflowTaskActionRequest(null) : request;
        return ApiResponse.ok(workflowApplicationService.terminateInstance(instanceId, safeRequest.toCommand()));
    }

    @Operation(summary = "查询我的待办")
    @GetMapping("/tasks/todo")
    @SaCheckPermission(PermissionCodes.WORKFLOW_READ)
    public ApiResponse<PageResult<WorkflowTaskView>> todoTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long taskId
    ) {
        return ApiResponse.ok(workflowApplicationService.todoTasks(page, size, taskId));
    }

    @Operation(summary = "查询我的已办")
    @GetMapping("/tasks/done")
    @SaCheckPermission(PermissionCodes.WORKFLOW_READ)
    public ApiResponse<PageResult<WorkflowTaskView>> doneTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(workflowApplicationService.doneTasks(page, size));
    }

    @Operation(summary = "审批通过任务")
    @PutMapping("/tasks/{taskId}/approve")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowActionResult> approveTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId,
            @Valid @RequestBody(required = false) WorkflowTaskActionRequest request
    ) {
        WorkflowTaskActionRequest safeRequest = request == null ? new WorkflowTaskActionRequest(null) : request;
        return ApiResponse.ok(workflowApplicationService.approveTask(taskId, safeRequest.toCommand()));
    }

    @Operation(summary = "驳回任务")
    @PutMapping("/tasks/{taskId}/reject")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowActionResult> rejectTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId,
            @Valid @RequestBody(required = false) WorkflowTaskActionRequest request
    ) {
        WorkflowTaskActionRequest safeRequest = request == null ? new WorkflowTaskActionRequest(null) : request;
        return ApiResponse.ok(workflowApplicationService.rejectTask(taskId, safeRequest.toCommand()));
    }

    @Operation(summary = "转签任务")
    @PutMapping("/tasks/{taskId}/transfer")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowActionResult> transferTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId,
            @Valid @RequestBody WorkflowTaskTransferRequest request
    ) {
        return ApiResponse.ok(workflowApplicationService.transferTask(taskId, request.toCommand()));
    }

    @Operation(summary = "催办任务")
    @PutMapping("/tasks/{taskId}/urge")
    @SaCheckPermission(PermissionCodes.WORKFLOW_WRITE)
    public ApiResponse<WorkflowTaskUrgeResult> urgeTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId,
            @Valid @RequestBody(required = false) WorkflowTaskUrgeRequest request
    ) {
        WorkflowTaskUrgeRequest safeRequest = request == null ? new WorkflowTaskUrgeRequest(null) : request;
        return ApiResponse.ok(urgeService.urge(taskId, safeRequest.toCommand()));
    }

    @Operation(summary = "查询任务催办历史")
    @GetMapping("/tasks/{taskId}/urges")
    @SaCheckPermission(PermissionCodes.WORKFLOW_READ)
    public ApiResponse<List<WorkflowTaskUrgeView>> taskUrges(@Parameter(description = "任务 ID") @PathVariable Long taskId) {
        return ApiResponse.ok(urgeService.listUrges(taskId));
    }

    @Operation(summary = "分页查询流程实例催办记录")
    @GetMapping("/instances/{instanceId}/urges")
    @SaCheckPermission(PermissionCodes.WORKFLOW_READ)
    public ApiResponse<PageResult<WorkflowTaskUrgeView>> instanceUrges(
            @Parameter(description = "流程实例 ID") @PathVariable Long instanceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(urgeService.listUrgesByInstance(instanceId, page, size));
    }
}