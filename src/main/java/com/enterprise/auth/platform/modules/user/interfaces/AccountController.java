package com.enterprise.auth.platform.modules.user.interfaces;

import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.user.application.AccountApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "个人账户")
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountApplicationService accountApplicationService;

    public AccountController(AccountApplicationService accountApplicationService) {
        this.accountApplicationService = accountApplicationService;
    }

    @Operation(summary = "获取当前账户资料")
    @GetMapping("/profile")
    public ApiResponse<AccountProfileResponse> profile() {
        return ApiResponse.ok(accountApplicationService.profile());
    }

    @Operation(summary = "更新当前账户资料")
    @PutMapping("/profile")
    public ApiResponse<AccountProfileResponse> updateProfile(@Valid @RequestBody AccountProfileUpdateRequest request) {
        return ApiResponse.ok(accountApplicationService.updateProfile(request));
    }

    @Operation(summary = "更新当前账户头像（兼容旧路径）")
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AccountProfileResponse> updateAvatar(
            @Parameter(description = "头像文件") @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(accountApplicationService.updateAvatar(file));
    }

    @Operation(summary = "更新当前账户头像")
    @PutMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AccountProfileResponse> updateProfileAvatar(
            @Parameter(description = "头像文件") @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(accountApplicationService.updateAvatar(file));
    }

    @Operation(summary = "修改当前账户密码")
    @PostMapping("/password/change")
    public ApiResponse<AccountProfileResponse> changePassword(@Valid @RequestBody AccountPasswordChangeRequest request) {
        return ApiResponse.ok(accountApplicationService.changePassword(request));
    }
}