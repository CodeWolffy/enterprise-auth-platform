package com.enterprise.auth.platform.modules.workflow.infrastructure;

import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskCandidateRoleEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskCandidateUserEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskCandidateRoleMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskCandidateUserMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Writes the normalized candidate relation tables during the workflow
 * expand-contract migration. JSON candidate fields remain owned by the task
 * repository for backward compatibility.
 */
final class WorkflowCandidateLinkWriter {

    private static final int BATCH_SIZE = 500;

    private final WfTaskCandidateUserMapper userMapper;
    private final WfTaskCandidateRoleMapper roleMapper;

    WorkflowCandidateLinkWriter(
            WfTaskCandidateUserMapper userMapper,
            WfTaskCandidateRoleMapper roleMapper
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    void write(WorkflowTask task) {
        if (task == null || task.getId() == null || !StringUtils.hasText(task.getTenantId())) {
            return;
        }
        insertUserLinks(task);
        insertRoleLinks(task);
    }

    private void insertUserLinks(WorkflowTask task) {
        List<WfTaskCandidateUserEntity> links = new ArrayList<>();
        if (task.getCandidateUserIds() != null) {
            for (Long userId : task.getCandidateUserIds()) {
                if (userId == null) {
                    continue;
                }
                WfTaskCandidateUserEntity link = new WfTaskCandidateUserEntity();
                link.setTenantId(task.getTenantId());
                link.setTaskId(task.getId());
                link.setUserId(userId);
                links.add(link);
            }
        }
        insertInBatches(links, userMapper::insertIgnoreBatch);
    }

    private void insertRoleLinks(WorkflowTask task) {
        List<WfTaskCandidateRoleEntity> links = new ArrayList<>();
        if (task.getCandidateGroupCodes() != null) {
            for (String roleCode : task.getCandidateGroupCodes()) {
                if (!StringUtils.hasText(roleCode)) {
                    continue;
                }
                WfTaskCandidateRoleEntity link = new WfTaskCandidateRoleEntity();
                link.setTenantId(task.getTenantId());
                link.setTaskId(task.getId());
                link.setRoleCode(roleCode.trim());
                links.add(link);
            }
        }
        insertInBatches(links, roleMapper::insertIgnoreBatch);
    }

    private <T> void insertInBatches(List<T> links, BatchInsert<T> insert) {
        for (int start = 0; start < links.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, links.size());
            insert.execute(links.subList(start, end));
        }
    }

    @FunctionalInterface
    private interface BatchInsert<T> {
        int execute(List<T> links);
    }
}
