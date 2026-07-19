package com.enterprise.auth.platform.modules.system.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理")
@RestController
@RequestMapping("/api/system")
public class SystemFeatureController {

    @Operation(summary = "查询预留组件状态")
    @GetMapping("/features")
    @SaCheckPermission(PermissionCodes.SYSTEM_GET)
    public ApiResponse<Map<String, Boolean>> features() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("gatewayEnabled", false);
        features.put("nacosEnabled", false);
        features.put("mqEnabled", false);
        features.put("seataEnabled", false);
        features.put("jobEnabled", false);
        features.put("lokiEnabled", false);
        return ApiResponse.ok(features);
    }
}
