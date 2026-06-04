package com.enterprise.auth.platform.modules.user.interfaces;

import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.user.application.AccountApplicationService;
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

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountApplicationService accountApplicationService;

    public AccountController(AccountApplicationService accountApplicationService) {
        this.accountApplicationService = accountApplicationService;
    }

    @GetMapping("/profile")
    public ApiResponse<AccountProfileResponse> profile() {
        return ApiResponse.ok(accountApplicationService.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<AccountProfileResponse> updateProfile(@Valid @RequestBody AccountProfileUpdateRequest request) {
        return ApiResponse.ok(accountApplicationService.updateProfile(request));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AccountProfileResponse> updateAvatar(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(accountApplicationService.updateAvatar(file));
    }

    @PutMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AccountProfileResponse> updateProfileAvatar(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(accountApplicationService.updateAvatar(file));
    }

    @PostMapping("/password/change")
    public ApiResponse<AccountProfileResponse> changePassword(@Valid @RequestBody AccountPasswordChangeRequest request) {
        return ApiResponse.ok(accountApplicationService.changePassword(request));
    }
}