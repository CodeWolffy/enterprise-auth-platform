package com.enterprise.auth.platform.modules.workflow.domain;

import com.enterprise.auth.platform.common.exception.BusinessException;

/**
 * 驳回策略状态机：给定当前节点的驳回策略与位置，计算驳回后应跳转的目标节点索引。
 *
 * <p>返回值语义：
 * <ul>
 *   <li>{@link #TARGET_END}：驳回即结束流程（实例置为 REJECTED）。</li>
 *   <li>{@link #TARGET_STARTER}：驳回回到发起人重提（创建发起人重提任务）。</li>
 *   <li>{@code >= 0}：驳回回到指定索引的审批节点。</li>
 * </ul>
 *
 * <p>纯逻辑，不依赖 Spring / 持久化，可独立单测。
 */
public final class WorkflowRejectResolver {

    public static final int TARGET_END = -1;
    public static final int TARGET_STARTER = -2;

    private WorkflowRejectResolver() {
    }

    /**
     * 解析驳回目标节点。
     *
     * @param strategy         当前节点的驳回策略，null 视为 END
     * @param rejectTarget     TO_STEP 策略配置的目标节点索引，其他策略忽略
     * @param currentStepIndex 当前节点索引
     * @param totalSteps       流程节点总数
     */
    public static int resolveTarget(WorkflowRejectStrategy strategy, Integer rejectTarget, int currentStepIndex, int totalSteps) {
        if (strategy == null) {
            return TARGET_END;
        }
        return switch (strategy) {
            case END -> TARGET_END;
            case PREVIOUS -> currentStepIndex <= 0 ? TARGET_END : currentStepIndex - 1;
            case RESTART -> totalSteps <= 0 ? TARGET_END : 0;
            case TO_STEP -> resolveExplicitTarget(rejectTarget, currentStepIndex, totalSteps);
            case TO_STARTER -> TARGET_STARTER;
        };
    }

    private static int resolveExplicitTarget(Integer rejectTarget, int currentStepIndex, int totalSteps) {
        if (rejectTarget == null) {
            throw new BusinessException("指定节点驳回需要配置目标节点");
        }
        if (rejectTarget < 0 || rejectTarget >= totalSteps) {
            throw new BusinessException("指定节点驳回目标不存在");
        }
        if (rejectTarget >= currentStepIndex) {
            throw new BusinessException("指定节点驳回只能指向当前节点之前的节点");
        }
        return rejectTarget;
    }
}
