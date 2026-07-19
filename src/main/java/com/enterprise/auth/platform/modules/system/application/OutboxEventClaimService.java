package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysOutboxEventEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysOutboxEventMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在短事务内回收超时事件并认领待投递事件。
 */
@Service
public class OutboxEventClaimService {

    static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(10);

    private final SysOutboxEventMapper outboxEventMapper;

    public OutboxEventClaimService(SysOutboxEventMapper outboxEventMapper) {
        this.outboxEventMapper = outboxEventMapper;
    }

    @Transactional
    public List<SysOutboxEventEntity> claimBatch(Instant now, int limit) {
        outboxEventMapper.recoverStaleProcessing(now.minus(PROCESSING_TIMEOUT), now);
        List<SysOutboxEventEntity> candidates = outboxEventMapper.claimCandidates(now, limit);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<SysOutboxEventEntity> claimed = new ArrayList<>(candidates.size());
        for (SysOutboxEventEntity event : candidates) {
            if (event == null || event.getId() == null) {
                continue;
            }
            if (outboxEventMapper.markProcessing(event.getId()) == 1) {
                claimed.add(event);
            }
        }
        return List.copyOf(claimed);
    }
}
