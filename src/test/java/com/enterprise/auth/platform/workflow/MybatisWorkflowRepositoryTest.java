package com.enterprise.auth.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import com.enterprise.auth.platform.modules.workflow.infrastructure.MybatisWorkflowRepository;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskCandidateRoleEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskCandidateUserEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessDefinitionMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessInstanceMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskCandidateRoleMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskCandidateUserMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskUrgeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisWorkflowRepositoryTest {

    private final WfTaskMapper taskMapper = mock(WfTaskMapper.class);
    private final WfTaskCandidateUserMapper candidateUserMapper = mock(WfTaskCandidateUserMapper.class);
    private final WfTaskCandidateRoleMapper candidateRoleMapper = mock(WfTaskCandidateRoleMapper.class);
    private final MybatisWorkflowRepository repository = new MybatisWorkflowRepository(
            mock(WfProcessDefinitionMapper.class),
            mock(WfProcessInstanceMapper.class),
            taskMapper,
            mock(WfTaskUrgeMapper.class),
            candidateUserMapper,
            candidateRoleMapper,
            new ObjectMapper()
    );

    @Test
    @SuppressWarnings("unchecked")
    void insertTaskShouldKeepJsonAndWriteCandidateLinksInBatches() {
        Set<Long> userIds = LongStream.rangeClosed(1, 501)
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        WorkflowTask task = task(userIds, new LinkedHashSet<>(List.of(" APPROVER ", " ", "REVIEWER")));

        repository.insertTask(task);

        ArgumentCaptor<WfTaskEntity> taskEntity = ArgumentCaptor.forClass(WfTaskEntity.class);
        verify(taskMapper).insert(taskEntity.capture());
        assertThat(taskEntity.getValue().getCandidateUserIdsJson()).contains("1", "501");
        assertThat(taskEntity.getValue().getCandidateGroupCodesJson()).contains("APPROVER", "REVIEWER");

        ArgumentCaptor<List<WfTaskCandidateUserEntity>> userBatches = ArgumentCaptor.forClass(List.class);
        verify(candidateUserMapper, times(2)).insertIgnoreBatch(userBatches.capture());
        assertThat(userBatches.getAllValues()).extracting(List::size).containsExactly(500, 1);
        assertThat(userBatches.getAllValues()).flatExtracting(batch -> batch)
                .extracting(WfTaskCandidateUserEntity::getUserId)
                .containsExactlyElementsOf(userIds);

        ArgumentCaptor<List<WfTaskCandidateRoleEntity>> roleBatches = ArgumentCaptor.forClass(List.class);
        verify(candidateRoleMapper).insertIgnoreBatch(roleBatches.capture());
        assertThat(roleBatches.getValue()).extracting(WfTaskCandidateRoleEntity::getRoleCode)
                .containsExactly("APPROVER", "REVIEWER");
        verify(candidateUserMapper, never()).insert(any(WfTaskCandidateUserEntity.class));
        verify(candidateRoleMapper, never()).insert(any(WfTaskCandidateRoleEntity.class));
    }

    @Test
    void insertTaskShouldTreatIgnoredDuplicateLinksAsSuccess() {
        WorkflowTask task = task(Set.of(7L), Set.of("APPROVER"));
        when(candidateUserMapper.insertIgnoreBatch(anyList())).thenReturn(0);
        when(candidateRoleMapper.insertIgnoreBatch(anyList())).thenReturn(0);

        assertThatCode(() -> repository.insertTask(task)).doesNotThrowAnyException();

        verify(candidateUserMapper).insertIgnoreBatch(anyList());
        verify(candidateRoleMapper).insertIgnoreBatch(anyList());
    }

    @Test
    void insertTaskShouldNotHideNonDuplicateDatabaseFailures() {
        WorkflowTask task = task(Set.of(7L), Set.of());
        when(candidateUserMapper.insertIgnoreBatch(anyList())).thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> repository.insertTask(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    @Test
    void candidateBatchStatementsShouldIgnoreUniqueKeyConflicts() throws NoSuchMethodException {
        Insert userInsert = WfTaskCandidateUserMapper.class
                .getMethod("insertIgnoreBatch", List.class)
                .getAnnotation(Insert.class);
        Insert roleInsert = WfTaskCandidateRoleMapper.class
                .getMethod("insertIgnoreBatch", List.class)
                .getAnnotation(Insert.class);

        assertThat(String.join(" ", userInsert.value())).contains("INSERT IGNORE INTO wf_task_candidate_user");
        assertThat(String.join(" ", roleInsert.value())).contains("INSERT IGNORE INTO wf_task_candidate_role");
    }

    private static WorkflowTask task(Set<Long> userIds, Set<String> roleCodes) {
        WorkflowTask task = new WorkflowTask();
        task.setId(42L);
        task.setTenantId("tenant-a");
        task.setInstanceId(11L);
        task.setDefinitionId(12L);
        task.setStepIndex(0);
        task.setStepName("审批");
        task.setStatus(WorkflowTaskStatus.PENDING);
        task.setCandidateUserIds(userIds);
        task.setCandidateGroupCodes(roleCodes);
        return task;
    }
}
