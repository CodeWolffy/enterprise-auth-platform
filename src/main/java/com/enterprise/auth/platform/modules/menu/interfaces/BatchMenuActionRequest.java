package com.enterprise.auth.platform.modules.menu.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "批量生成按钮权限请求")
public record BatchMenuActionRequest(
        @Schema(description = "动作集合：read/create/update/delete/export/import") @NotEmpty List<String> actions
) {
}