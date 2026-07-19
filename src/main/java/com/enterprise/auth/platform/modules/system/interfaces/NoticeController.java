package com.enterprise.auth.platform.modules.system.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import com.enterprise.auth.platform.modules.system.application.NoticeApplicationService;
import com.enterprise.auth.platform.modules.system.application.SystemViewModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理")
@RestController
@RequestMapping("/api/system/notices")
public class NoticeController {

    private final NoticeApplicationService noticeApplicationService;

    public NoticeController(NoticeApplicationService noticeApplicationService) {
        this.noticeApplicationService = noticeApplicationService;
    }

    @Operation(summary = "分页查询公告列表")
    @GetMapping
    @SaCheckPermission(PermissionCodes.SYSNOTICE_PAGE)
    public ApiResponse<PageResult<SystemViewModels.NoticeView>> notices(
            @Parameter(description = "是否已发布") @RequestParam(required = false) Boolean published,
            @Parameter(description = "工作流状态：DRAFT、SCHEDULED、PUBLISHED") @RequestParam(required = false) String workflowStatus,
            @Parameter(description = "关键字，匹配标题或内容") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段：publishTime、createdAt、noticeTitle") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc") @RequestParam(required = false) String sortDirection
    ) {
        return ApiResponse.ok(noticeApplicationService.notices(published, workflowStatus, keyword, page, size, sortBy, sortDirection));
    }

    @Operation(summary = "查询公告详情")
    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SYSNOTICE_GET)
    public ApiResponse<SystemViewModels.NoticeView> noticeDetail(@Parameter(description = "公告 ID") @PathVariable Long id) {
        return ApiResponse.ok(noticeApplicationService.noticeDetail(id));
    }

    @Operation(summary = "查询已发布公告详情")
    @GetMapping("/{id}/published")
    public ApiResponse<SystemViewModels.NoticeView> publishedNotice(@Parameter(description = "公告 ID") @PathVariable Long id) {
        return ApiResponse.ok(noticeApplicationService.publishedNotice(id));
    }

    @SysLog("新增公告")
    @Operation(summary = "新增公告")
    @PostMapping
    @SaCheckPermission(PermissionCodes.SYSNOTICE_ADD)
    public ApiResponse<SystemViewModels.NoticeView> createNotice(@Valid @RequestBody NoticeCrudRequest request) {
        return ApiResponse.ok(noticeApplicationService.createNotice(request));
    }

    @SysLog("修改公告")
    @Operation(summary = "修改公告")
    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SYSNOTICE_EDIT)
    public ApiResponse<SystemViewModels.NoticeView> updateNotice(
            @Parameter(description = "公告 ID") @PathVariable Long id,
            @Valid @RequestBody NoticeCrudRequest request
    ) {
        return ApiResponse.ok(noticeApplicationService.updateNotice(id, request));
    }

    @SysLog("删除公告")
    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SYSNOTICE_DEL)
    public ApiResponse<Void> deleteNotice(@Parameter(description = "公告 ID") @PathVariable Long id) {
        noticeApplicationService.deleteNotice(id);
        return ApiResponse.ok();
    }
}
