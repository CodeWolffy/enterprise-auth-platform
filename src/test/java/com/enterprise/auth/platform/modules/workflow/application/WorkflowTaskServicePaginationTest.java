package com.enterprise.auth.platform.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRepository;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowTaskServicePaginationTest {

    private static final String TENANT_ID = "tenant-a";
    private static final Long USER_ID = 7L;

    private final WorkflowRepository repository = mock(WorkflowRepository.class);
    private final WorkflowStore store = mock(WorkflowStore.class);
    private final WorkflowViewMapper viewMapper = mock(WorkflowViewMapper.class);
    private final WorkflowNotifier notifier = mock(WorkflowNotifier.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final WorkflowTaskService service = new WorkflowTaskService(
            repository, store, viewMapper, notifier, currentUserService);
    private final UserAccount user = new UserAccount(
            USER_ID,
            TENANT_ID,
            "approver",
            "hash",
            true,
            Set.of("APPROVER"),
            Set.of(),
            Set.of(),
            DataScopeType.ALL,
            1);

    @BeforeEach
    void setUp() {
        when(currentUserService.requireCurrentUser()).thenReturn(user);
    }

    @Test
    void todoShouldCountAndPageInDatabaseBeyondFormerCandidateCap() {
        WorkflowTask task = task(601L, WorkflowTaskStatus.PENDING);
        WorkflowTaskView view = mock(WorkflowTaskView.class);
        when(repository.countTodoCandidates(TENANT_ID, USER_ID, null)).thenReturn(601L);
        when(repository.findTodoCandidates(TENANT_ID, USER_ID, null, 500, 100))
                .thenReturn(List.of(task));
        when(repository.countUrgesByTaskIds(TENANT_ID, List.of(601L))).thenReturn(Map.of(601L, 4L));
        when(viewMapper.toTodoTaskViews(anyCollection(), eq(user), anyMap())).thenReturn(List.of(view));

        var result = service.todoTasks(6, 100);

        assertThat(result.total()).isEqualTo(601L);
        assertThat(result.page()).isEqualTo(6);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.records()).containsExactly(view);
        verify(repository).countTodoCandidates(TENANT_ID, USER_ID, null);
        verify(repository).findTodoCandidates(TENANT_ID, USER_ID, null, 500, 100);
        verify(repository).countUrgesByTaskIds(TENANT_ID, List.of(601L));
    }

    @Test
    void todoTaskIdFilterShouldRemainExactAndUseCurrentPageForUrgeCounts() {
        WorkflowTask task = task(42L, WorkflowTaskStatus.PENDING);
        WorkflowTaskView view = mock(WorkflowTaskView.class);
        when(repository.countTodoCandidates(TENANT_ID, USER_ID, 42L)).thenReturn(1L);
        when(repository.findTodoCandidates(TENANT_ID, USER_ID, 42L, 0, 20))
                .thenReturn(List.of(task));
        when(repository.countUrgesByTaskIds(TENANT_ID, List.of(42L))).thenReturn(Map.of(42L, 2L));
        when(viewMapper.toTodoTaskViews(anyCollection(), eq(user), anyMap())).thenReturn(List.of(view));

        var result = service.todoTasks(1, 20, 42L);

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).containsExactly(view);
        verify(repository).countTodoCandidates(TENANT_ID, USER_ID, 42L);
        verify(repository).findTodoCandidates(TENANT_ID, USER_ID, 42L, 0, 20);
        verify(repository).countUrgesByTaskIds(TENANT_ID, List.of(42L));
    }

    @Test
    void doneShouldKeepDatabaseCountAndOffsetPagination() {
        WorkflowTask task = task(202L, WorkflowTaskStatus.APPROVED);
        WorkflowTaskView view = mock(WorkflowTaskView.class);
        when(repository.countDoneTasks(TENANT_ID, USER_ID)).thenReturn(201L);
        when(repository.findDoneTasks(TENANT_ID, USER_ID, 100, 100)).thenReturn(List.of(task));
        when(repository.countUrgesByTaskIds(TENANT_ID, List.of(202L))).thenReturn(Map.of(202L, 3L));
        when(viewMapper.toTaskViews(anyCollection(), eq(user), anyMap())).thenReturn(List.of(view));

        var result = service.doneTasks(2, 100);

        assertThat(result.total()).isEqualTo(201L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.records()).containsExactly(view);
        verify(repository).countDoneTasks(TENANT_ID, USER_ID);
        verify(repository).findDoneTasks(TENANT_ID, USER_ID, 100, 100);
        verify(repository).countUrgesByTaskIds(TENANT_ID, List.of(202L));
    }

    private WorkflowTask task(Long id, WorkflowTaskStatus status) {
        WorkflowTask task = new WorkflowTask();
        task.setId(id);
        task.setTenantId(TENANT_ID);
        task.setStatus(status);
        task.setCandidateUserIds(Set.of(USER_ID));
        task.setCandidateGroupCodes(Set.of());
        return task;
    }
}
