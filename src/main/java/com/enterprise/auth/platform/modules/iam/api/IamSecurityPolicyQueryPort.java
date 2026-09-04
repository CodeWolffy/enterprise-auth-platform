package com.enterprise.auth.platform.modules.iam.api;

import com.enterprise.auth.platform.common.security.EffectiveSecurityPolicy;

/** IAM security-policy query contract implemented by the security module. */
public interface IamSecurityPolicyQueryPort {

    EffectiveSecurityPolicy effectivePolicy(String tenantId);
}
