package com.enterprise.auth.platform.modules.user.interfaces;

import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.user.application.AccountApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/password/change")
    public ApiResponse<AccountProfileResponse> changePassword(@Valid @RequestBody AccountPasswordChangeRequest request) {
        return ApiResponse.ok(accountApplicationService.changePassword(request));
    }
}