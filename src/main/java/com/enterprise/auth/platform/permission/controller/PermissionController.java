package com.enterprise.auth.platform.permission.controller;

import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "权限目录")
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final CatalogService catalogService;

    public PermissionController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Operation(summary = "查询权限目录")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('role:read','role:write')")
    public ApiResponse<List<CatalogService.PermissionView>> list() {
        return ApiResponse.ok(catalogService.permissions());
    }
}
