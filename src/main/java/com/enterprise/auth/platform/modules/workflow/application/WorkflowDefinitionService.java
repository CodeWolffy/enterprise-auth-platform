package com.enterprise.auth.platform.modules.workflow.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.role.application.RoleQueryFacade;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinitionStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectStrategy;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessDefinitionEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessDefinitionMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 流程定义应用服务：草稿创建、部署、停用、详情与分页查询，以及步骤规范化与候选人校验。
 */
@Service
public class WorkflowDefinitionService {

    private final WfProcessDefinitionMapper definitionMapper;
    private final WorkflowStore store;
    private final WorkflowViewMapper viewMapper;
    private final WorkflowCodec codec;
    private final UserQueryFacade userQueryFacade;
    private final RoleQueryFacade roleQueryFacade;
    private final CurrentUserService currentUserService;

    public WorkflowDefinitionService(
            WfProcessDefinitionMapper definitionMapper,
            WorkflowStore store,
            WorkflowViewMapper viewMapper,
            WorkflowCodec codec,
            UserQueryFacade userQueryFacade,
            RoleQueryFacade roleQueryFacade,
            CurrentUserService currentUserService
    ) {
        this.definitionMapper = definitionMapper;
        this.store = store;
        this.viewMapper = viewMapper;
        this.codec = codec;
        this.userQueryFacade = userQueryFacade;
        this.roleQueryFacade = roleQueryFacade;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public WorkflowDefinitionView createDefinition(WorkflowDefinitionCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        List<WorkflowStepDefinition> steps = normalizeSteps(command.steps());
        validateCandidates(tenantId, steps);
        int version = store.nextDefinitionVersion(tenantId, command.definitionKey());

        WfProcessDefinitionEntity entity = new WfProcessDefinitionEntity();
        entity.setTenantId(tenantId);
        entity.setDefinitionKey(command.definitionKey().trim());
        entity.setDefinitionName(command.definitionName().trim());
        entity.setVersion(version);
        entity.setStatus(WorkflowDefinitionStatus.DRAFT.name());
        entity.setStepsJson(codec.toJson(steps));
        entity.setRemark(WorkflowSupport.normalizeText(command.remark()));
        definitionMapper.insert(entity);
        return viewMapper.toDefinitionView(entity);
    }

    @Transactional
    public WorkflowDefinitionView deployDefinition(Long definitionId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfProcessDefinitionEntity entity = store.requireDefinition(tenantId, definitionId);
        if (WorkflowDefinitionStatus.DISABLED.name().equals(entity.getStatus())) {
            throw new BusinessException("已停用流程定义不能重新部署，请创建新版本");
        }
        entity.setStatus(WorkflowDefinitionStatus.DEPLOYED.name());
        definitionMapper.updateById(entity);
        return viewMapper.toDefinitionView(entity);
    }

    @Transactional
    public WorkflowDefinitionView disableDefinition(Long definitionId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfProcessDefinitionEntity entity = store.requireDefinition(tenantId, definitionId);
        entity.setStatus(WorkflowDefinitionStatus.DISABLED.name());
        definitionMapper.updateById(entity);
        return viewMapper.toDefinitionView(entity);
    }

    public WorkflowDefinitionView definition(Long definitionId) {
        UserAccount user = currentUserService.requireCurrentUser();
        return viewMapper.toDefinitionView(store.requireDefinition(WorkflowSupport.currentTenantId(user), definitionId));
    }

    public PageResult<WorkflowDefinitionView> definitions(String status, int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        int normalizedPage = PaginationSupport.normalizePage(page);
        int normalizedSize = WorkflowSupport.normalizeSize(size);
        boolean globalScope = TenantContext.isGlobalScope();
        LambdaQueryWrapper<WfProcessDefinitionEntity> wrapper = new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(!globalScope, WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getDeleted, 0);
        if (StringUtils.hasText(status)) {
            wrapper.eq(WfProcessDefinitionEntity::getStatus, status.trim().toUpperCase());
        }
        long total = definitionMapper.selectCount(wrapper);
        int offset = (normalizedPage - 1) * normalizedSize;
        wrapper.orderByDesc(WfProcessDefinitionEntity::getUpdatedAt)
                .orderByDesc(WfProcessDefinitionEntity::getId)
                .last("limit " + offset + "," + normalizedSize);
        List<WorkflowDefinitionView> records = definitionMapper.selectList(wrapper).stream()
                .map(viewMapper::toDefinitionView)
                .toList();
        return PageResult.of(total, normalizedPage, normalizedSize, records);
    }

    private List<WorkflowStepDefinition> normalizeSteps(List<WorkflowStepDefinition> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new BusinessException("流程至少需要一个审批步骤");
        }
        List<WorkflowStepDefinition> normalized = new ArrayList<>();
        for (WorkflowStepDefinition step : steps) {
            if (step == null || !StringUtils.hasText(step.name())) {
                throw new BusinessException("审批步骤名称不能为空");
            }
            Set<Long> userIds = step.candidateUserIds().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> groupCodes = step.candidateGroupCodes().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (userIds.isEmpty() && groupCodes.isEmpty()) {
                throw new BusinessException("审批步骤至少需要一个候选人或候选组");
            }
            WorkflowRejectStrategy rejectStrategy = step.rejectStrategy() == null ? WorkflowRejectStrategy.END : step.rejectStrategy();
            Integer rejectTarget = normalizeRejectTarget(rejectStrategy, step.rejectTarget(), normalized.size(), steps.size());
            normalized.add(new WorkflowStepDefinition(step.name().trim(), userIds, groupCodes, rejectStrategy, rejectTarget));
        }
        return normalized;
    }

    private Integer normalizeRejectTarget(WorkflowRejectStrategy strategy, Integer rejectTarget, int stepIndex, int totalSteps) {
        if (strategy != WorkflowRejectStrategy.TO_STEP) {
            return null;
        }
        if (rejectTarget == null) {
            throw new BusinessException("指定节点驳回需要配置目标节点");
        }
        if (rejectTarget < 0 || rejectTarget >= totalSteps) {
            throw new BusinessException("指定节点驳回目标不存在");
        }
        if (rejectTarget >= stepIndex) {
            throw new BusinessException("指定节点驳回只能指向当前节点之前的节点");
        }
        return rejectTarget;
    }

    private void validateCandidates(String tenantId, List<WorkflowStepDefinition> steps) {
        Set<Long> userIds = new LinkedHashSet<>();
        Set<String> roleCodes = new LinkedHashSet<>();
        for (WorkflowStepDefinition step : steps) {
            userIds.addAll(step.candidateUserIds());
            roleCodes.addAll(step.candidateGroupCodes());
        }
        if (!userIds.isEmpty()) {
            long validUsers = userQueryFacade.countExistingByIds(tenantId, userIds);
            if (validUsers != userIds.size()) {
                throw new BusinessException("存在无效的候选人");
            }
        }
        if (!roleCodes.isEmpty()) {
            long validRoles = roleQueryFacade.countByCodes(tenantId, roleCodes);
            if (validRoles != roleCodes.size()) {
                throw new BusinessException("存在无效的候选组");
            }
        }
    }
}
