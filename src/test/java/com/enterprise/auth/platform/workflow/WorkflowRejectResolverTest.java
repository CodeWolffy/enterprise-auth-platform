package com.enterprise.auth.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectResolver;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectStrategy;
import org.junit.jupiter.api.Test;

class WorkflowRejectResolverTest {

    @Test
    void endStrategyAlwaysEndsInstance() {
        assertThat(WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.END, null, 2, 3))
                .isEqualTo(WorkflowRejectResolver.TARGET_END);
    }

    @Test
    void nullStrategyDegradesToEnd() {
        assertThat(WorkflowRejectResolver.resolveTarget(null, null, 2, 3))
                .isEqualTo(WorkflowRejectResolver.TARGET_END);
    }

    @Test
    void previousStrategyReturnsPriorNode() {
        assertThat(WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.PREVIOUS, null, 2, 3))
                .isEqualTo(1);
    }

    @Test
    void previousStrategyDegradesToEndOnFirstNode() {
        assertThat(WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.PREVIOUS, null, 0, 3))
                .isEqualTo(WorkflowRejectResolver.TARGET_END);
    }

    @Test
    void restartStrategyReturnsFirstNode() {
        assertThat(WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.RESTART, null, 2, 3))
                .isEqualTo(0);
    }

    @Test
    void restartStrategyDegradesToEndWhenNoSteps() {
        assertThat(WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.RESTART, null, 0, 0))
                .isEqualTo(WorkflowRejectResolver.TARGET_END);
    }

    @Test
    void toStarterStrategyReturnsStarterTarget() {
        assertThat(WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.TO_STARTER, null, 1, 2))
                .isEqualTo(WorkflowRejectResolver.TARGET_STARTER);
    }

    @Test
    void toStepStrategyReturnsConfiguredEarlierNode() {
        assertThat(WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.TO_STEP, 0, 2, 3))
                .isEqualTo(0);
    }

    @Test
    void toStepStrategyRejectsMissingTarget() {
        assertThatThrownBy(() -> WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.TO_STEP, null, 2, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标节点");
    }

    @Test
    void toStepStrategyRejectsForwardTarget() {
        assertThatThrownBy(() -> WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.TO_STEP, 2, 1, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("之前的节点");
    }

    @Test
    void toStepStrategyRejectsOutOfRangeTarget() {
        assertThatThrownBy(() -> WorkflowRejectResolver.resolveTarget(WorkflowRejectStrategy.TO_STEP, 5, 2, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }
}
