package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectStrategy;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * 工作流通用小工具：租户上下文归一、分页归一、文本清洗与越界步骤兜底。
 * 无状态纯函数，不依赖 Spring 容器。通用部分委托 common 的 TenantContextSupport / PaginationSupport。
 */
final class WorkflowSupport {

    private WorkflowSupport() {
    }

    static String currentTenantId(UserAccount user) {
        return TenantContextSupport.currentTenantIdOr(user.tenantId());
    }

    static int normalizeSize(int size) {
        return PaginationSupport.normalizeSize(size, 20, 100);
    }

    static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static <T> PageResult<T> page(List<T> items, int page, int size) {
        int normalizedPage = PaginationSupport.normalizePage(page);
        int normalizedSize = normalizeSize(size);
        long offset = PaginationSupport.offset(normalizedPage, normalizedSize);
        int from = (int) Math.min(offset, items.size());
        int to = (int) Math.min((long) items.size(), offset + normalizedSize);
        return PageResult.of(items.size(), normalizedPage, normalizedSize, items.subList(from, to));
    }

    /**
     * 越界或负索引（如发起人重提任务的 -1）返回默认 END 步骤，与原实现一致。
     */
    static WorkflowStepDefinition stepAt(List<WorkflowStepDefinition> steps, int index) {
        if (steps == null || index < 0 || index >= steps.size()) {
            return new WorkflowStepDefinition("未知步骤", Set.of(), Set.of(), WorkflowRejectStrategy.END);
        }
        return steps.get(index);
    }
}
