package com.enterprise.auth.platform.modules.security.interfaces;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SecurityPolicyRequest(
        @Min(6) @Max(128) Integer passwordMinLength,
        @Min(6) @Max(128) Integer passwordMaxLength,
        Boolean passwordRequireLetter,
        Boolean passwordRequireNumber,
        Boolean passwordRequireSpecial,
        @Min(0) @Max(24) Integer passwordHistoryCount,
        @Min(0) @Max(3650) Integer passwordExpireDays,
        @Min(1) @Max(20) Integer loginFailureMaxAttempts,
        @Min(1) @Max(1440) Integer loginFailureLockMinutes,
        @Min(1) @Max(1440) Integer loginFailureWindowMinutes,
        Boolean captchaEnabled
) {
}