package com.enterprise.auth.platform.modules.workflow.application;

import java.util.Set;

/**
 * 工作流通知收件人集合：候选/指派用户与候选角色。
 */
record WorkflowRecipients(Set<Long> userIds, Set<String> roleCodes) {
}
