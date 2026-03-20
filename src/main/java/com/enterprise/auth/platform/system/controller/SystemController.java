package com.enterprise.auth.platform.system.controller;

import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.config.FeatureToggleProperties;
import com.enterprise.auth.platform.system.dto.ConfigCrudRequest;
import com.enterprise.auth.platform.system.dto.DictCrudRequest;
import com.enterprise.auth.platform.system.dto.NoticeCrudRequest;
import com.enterprise.auth.platform.system.service.SystemManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理")
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final FeatureToggleProperties featureToggleProperties;
    private final SystemManagementService systemManagementService;

    public SystemController(
            FeatureToggleProperties featureToggleProperties,
            SystemManagementService systemManagementService
    ) {
        this.featureToggleProperties = featureToggleProperties;
        this.systemManagementService = systemManagementService;
    }

    @Operation(summary = "查询预留组件开关")
    @GetMapping("/features")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<Map<String, Boolean>> features() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("gatewayEnabled", featureToggleProperties.gatewayEnabled());
        features.put("nacosEnabled", featureToggleProperties.nacosEnabled());
        features.put("mqEnabled", featureToggleProperties.mqEnabled());
        features.put("seataEnabled", featureToggleProperties.seataEnabled());
        features.put("jobEnabled", featureToggleProperties.jobEnabled());
        features.put("lokiEnabled", featureToggleProperties.lokiEnabled());
        return ApiResponse.ok(features);
    }

    @Operation(summary = "查询字典列表")
    @GetMapping("/dicts")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<List<SystemManagementService.DictView>> dicts() {
        return ApiResponse.ok(systemManagementService.dicts());
    }

    @Operation(summary = "新增字典")
    @PostMapping("/dicts")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.DictView> createDict(@Valid @RequestBody DictCrudRequest request) {
        return ApiResponse.ok(systemManagementService.createDict(request));
    }

    @Operation(summary = "修改字典")
    @PutMapping("/dicts/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.DictView> updateDict(
            @Parameter(description = "字典ID") @PathVariable Long id,
            @Valid @RequestBody DictCrudRequest request
    ) {
        return ApiResponse.ok(systemManagementService.updateDict(id, request));
    }

    @Operation(summary = "删除字典")
    @DeleteMapping("/dicts/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<Void> deleteDict(@Parameter(description = "字典ID") @PathVariable Long id) {
        systemManagementService.deleteDict(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询参数列表")
    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<List<SystemManagementService.ConfigView>> configs() {
        return ApiResponse.ok(systemManagementService.configs());
    }

    @Operation(summary = "新增参数")
    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.ConfigView> createConfig(@Valid @RequestBody ConfigCrudRequest request) {
        return ApiResponse.ok(systemManagementService.createConfig(request));
    }

    @Operation(summary = "修改参数")
    @PutMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.ConfigView> updateConfig(
            @Parameter(description = "参数ID") @PathVariable Long id,
            @Valid @RequestBody ConfigCrudRequest request
    ) {
        return ApiResponse.ok(systemManagementService.updateConfig(id, request));
    }

    @Operation(summary = "删除参数")
    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<Void> deleteConfig(@Parameter(description = "参数ID") @PathVariable Long id) {
        systemManagementService.deleteConfig(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询公告列表")
    @GetMapping("/notices")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<List<SystemManagementService.NoticeView>> notices() {
        return ApiResponse.ok(systemManagementService.notices());
    }

    @Operation(summary = "新增公告")
    @PostMapping("/notices")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.NoticeView> createNotice(@Valid @RequestBody NoticeCrudRequest request) {
        return ApiResponse.ok(systemManagementService.createNotice(request));
    }

    @Operation(summary = "修改公告")
    @PutMapping("/notices/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.NoticeView> updateNotice(
            @Parameter(description = "公告ID") @PathVariable Long id,
            @Valid @RequestBody NoticeCrudRequest request
    ) {
        return ApiResponse.ok(systemManagementService.updateNotice(id, request));
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/notices/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<Void> deleteNotice(@Parameter(description = "公告ID") @PathVariable Long id) {
        systemManagementService.deleteNotice(id);
        return ApiResponse.ok();
    }
}
