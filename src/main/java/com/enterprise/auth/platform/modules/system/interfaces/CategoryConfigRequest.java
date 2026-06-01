package com.enterprise.auth.platform.modules.system.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "系统分类配置请求")
public record CategoryConfigRequest(
        @Schema(description = "分类编码") @NotBlank String code,
        @Schema(description = "分类名称") @NotBlank String name,
        @Schema(description = "匹配规则集合") @NotEmpty List<String> matchers
) {
}
