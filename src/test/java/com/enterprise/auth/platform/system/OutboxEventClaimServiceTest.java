package com.enterprise.auth.platform.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.system.application.OutboxEventClaimService;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysOutboxEventEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysOutboxEventMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

class OutboxEventClaimServiceTest {

    @Test
    void claimBatchShouldRecoverTimedOutProcessingAndReturnOnlySuccessfullyClaimedEvents() {
        SysOutboxEventMapper mapper = mock(SysOutboxEventMapper.class);
        OutboxEventClaimService service = new OutboxEventClaimService(mapper);
        Instant now = Instant.parse("2026-07-12T08:00:00Z");
        SysOutboxEventEntity claimed = event(101L);
        SysOutboxEventEntity raced = event(102L);
        when(mapper.claimCandidates(now, 40)).thenReturn(List.of(claimed, raced));
        when(mapper.markProcessing(101L)).thenReturn(1);
        when(mapper.markProcessing(102L)).thenReturn(0);

        List<SysOutboxEventEntity> result = service.claimBatch(now, 40);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(mapper).recoverStaleProcessing(cutoff.capture(), org.mockito.ArgumentMatchers.eq(now));
        assertThat(Duration.between(cutoff.getValue(), now)).isEqualTo(Duration.ofMinutes(10));
        assertThat(result).containsExactly(claimed);
    }

    @Test
    void claimBatchShouldBeARealTransactionalBeanEntryPoint() throws Exception {
        Transactional annotation = AnnotatedElementUtils.findMergedAnnotation(
                OutboxEventClaimService.class.getMethod("claimBatch", Instant.class, int.class),
                Transactional.class
        );

        assertThat(annotation).isNotNull();
    }

    private SysOutboxEventEntity event(long id) {
        SysOutboxEventEntity event = new SysOutboxEventEntity();
        event.setId(id);
        event.setAttempts(0);
        event.setMaxAttempts(8);
        return event;
    }
}
