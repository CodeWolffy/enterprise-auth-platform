package com.enterprise.auth.platform.resource.controller;

import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.resource.dto.CreateResourceRequest;
import com.enterprise.auth.platform.resource.dto.SortResourceRequest;
import com.enterprise.auth.platform.resource.dto.UpdateResourceRequest;
import com.enterprise.auth.platform.resource.model.ResourceTreeNode;
import com.enterprise.auth.platform.resource.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "资源管理")
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Operation(summary = "查询资源树")
    @GetMapping("/tree")
    @SaCheckPermission("system:read")
    public ApiResponse<List<ResourceTreeNode>> tree() {
        return ApiResponse.ok(resourceService.templateTree());
    }

    @Operation(summary = "新增资源")
    @PostMapping
    @SaCheckPermission("system:write")
    public ApiResponse<ResourceTreeNode> create(@Valid @RequestBody CreateResourceRequest request) {
        return ApiResponse.ok(resourceService.createResource(request));
    }

    @Operation(summary = "修改资源")
    @PutMapping("/{resourceId}")
    @SaCheckPermission("system:write")
    public ApiResponse<ResourceTreeNode> update(
            @Parameter(description = "资源 ID") @PathVariable Long resourceId,
            @Valid @RequestBody UpdateResourceRequest request
    ) {
        return ApiResponse.ok(resourceService.updateResource(resourceId, request));
    }

    @Operation(summary = "删除资源")
    @DeleteMapping("/{resourceId}")
    @SaCheckPermission("system:write")
    public ApiResponse<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long resourceId) {
        resourceService.deleteResource(resourceId);
        return ApiResponse.ok();
    }

    @Operation(summary = "修改资源排序")
    @PutMapping("/{resourceId}/sort")
    @SaCheckPermission("system:write")
    public ApiResponse<ResourceTreeNode> sort(
            @Parameter(description = "资源 ID") @PathVariable Long resourceId,
            @Valid @RequestBody SortResourceRequest request
    ) {
        return ApiResponse.ok(resourceService.updateSort(resourceId, request.orderNo()));
    }
}
