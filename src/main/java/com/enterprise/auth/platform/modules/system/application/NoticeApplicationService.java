package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.HtmlSanitizer;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysNoticeEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysNoticeMapper;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.notification.application.NotificationScenarioPublisher;
import com.enterprise.auth.platform.modules.system.interfaces.NoticeCrudRequest;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NoticeApplicationService {

    private static final String SORT_ASC = "asc";
    private static final String SORT_DESC = "desc";

    private final SysNoticeMapper sysNoticeMapper;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;
    private final NotificationScenarioPublisher notificationScenarioPublisher;

    public NoticeApplicationService(
            SysNoticeMapper sysNoticeMapper,
            AuditService auditService,
            DataScopeService dataScopeService,
            NotificationScenarioPublisher notificationScenarioPublisher
    ) {
        this.sysNoticeMapper = sysNoticeMapper;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
        this.notificationScenarioPublisher = notificationScenarioPublisher;
    }

    @Cacheable(value = CacheNames.SYSTEM_NOTICES, key = "#root.target.generateCacheKey(new Object[]{#published, #workflowStatus, #keyword, #page, #size, #sortBy, #sortDirection})")
    public PageResult<SystemViewModels.NoticeView> notices(
            Boolean published,
            String workflowStatus,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        String tenantId = currentTenantId();
        Optional<Set<String>> visibleCreators = dataScopeService.visibleUsernames(tenantId);
        return pageQuery(
                buildNoticeQuery(tenantId, published, workflowStatus, keyword, visibleCreators),
                buildNoticeQuery(tenantId, published, workflowStatus, keyword, visibleCreators),
                page,
                size,
                sysNoticeMapper::selectCount,
                query -> sysNoticeMapper.selectList(query).stream().map(this::toNoticeView).toList(),
                resolveNoticeSort(sortBy),
                resolveDirection(sortDirection, SORT_DESC)
        );
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_NOTICES, allEntries = true)
    public SystemViewModels.NoticeView createNotice(NoticeCrudRequest request) {
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysNoticeEntity entity = new SysNoticeEntity();
        entity.setTenantId(tenantId);
        entity.setNoticeTitle(request.noticeTitle());
        entity.setNoticeContent(HtmlSanitizer.clean(request.noticeContent()));
        entity.setPublished(Boolean.TRUE.equals(request.published()) ? 1 : 0);
        entity.setPublishTime(TimeSupport.localDateTimeFromEpochMilli(request.publishTime()));
        sysNoticeMapper.insert(entity);
        auditService.record("NOTICE_CREATED", operator, tenantId, Map.of("noticeId", entity.getId(), "workflowStatus", workflowStatus(entity)));
        publishNoticeNotificationIfActive(entity, operator, false);
        return toNoticeView(entity);
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_NOTICES, allEntries = true)
    public SystemViewModels.NoticeView updateNotice(Long id, NoticeCrudRequest request) {
        String tenantId = currentTenantId();
        SysNoticeEntity entity = getNotice(id, tenantId);
        boolean wasActivePublished = activePublished(entity);
        entity.setNoticeTitle(request.noticeTitle());
        entity.setNoticeContent(HtmlSanitizer.clean(request.noticeContent()));
        entity.setPublished(Boolean.TRUE.equals(request.published()) ? 1 : 0);
        entity.setPublishTime(TimeSupport.localDateTimeFromEpochMilli(request.publishTime()));
        sysNoticeMapper.updateById(entity);
        auditService.record("NOTICE_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("noticeId", id, "workflowStatus", workflowStatus(entity)));
        publishNoticeNotificationIfActive(entity, SecuritySupport.currentOperator(), wasActivePublished);
        return toNoticeView(entity);
    }

    @Transactional
    @CacheEvict(value = CacheNames.SYSTEM_NOTICES, allEntries = true)
    public void deleteNotice(Long id) {
        String tenantId = currentTenantId();
        SysNoticeEntity entity = getNotice(id, tenantId);
        sysNoticeMapper.deleteById(entity.getId());
        auditService.record("NOTICE_DELETED", SecuritySupport.currentOperator(), tenantId, Map.of("noticeId", id));
    }

    public String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    public String generateCacheKey(Object... params) {
        StringBuilder key = new StringBuilder(currentTenantId())
                .append(':')
                .append(currentScopeCacheKey());
        for (Object param : params) {
            key.append(':').append(param == null ? "" : param);
        }
        return key.toString();
    }

    private SystemViewModels.NoticeView toNoticeView(SysNoticeEntity entity) {
        return new SystemViewModels.NoticeView(
                entity.getId(),
                entity.getNoticeTitle(),
                entity.getNoticeContent(),
                entity.getPublished() != null && entity.getPublished() == 1,
                TimeSupport.toEpochMilli(entity.getPublishTime()),
                workflowStatus(entity),
                entity.getCreatedBy()
        );
    }

    private LambdaQueryWrapper<SysNoticeEntity> buildNoticeQuery(
            String tenantId,
            Boolean published,
            String workflowStatus,
            String keyword,
            Optional<Set<String>> visibleCreators
    ) {
        LambdaQueryWrapper<SysNoticeEntity> query = new LambdaQueryWrapper<SysNoticeEntity>()
                .eq(SysNoticeEntity::getTenantId, tenantId)
                .eq(SysNoticeEntity::getDeleted, 0)
                .eq(published != null, SysNoticeEntity::getPublished, Boolean.TRUE.equals(published) ? 1 : 0)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysNoticeEntity::getNoticeTitle, keyword)
                        .or()
                        .like(SysNoticeEntity::getNoticeContent, keyword));
        if ("DRAFT".equalsIgnoreCase(workflowStatus)) {
            query.eq(SysNoticeEntity::getPublished, 0);
        } else if ("SCHEDULED".equalsIgnoreCase(workflowStatus)) {
            query.eq(SysNoticeEntity::getPublished, 1)
                    .isNotNull(SysNoticeEntity::getPublishTime)
                    .gt(SysNoticeEntity::getPublishTime, TimeSupport.utcNowDateTime());
        } else if ("PUBLISHED".equalsIgnoreCase(workflowStatus)) {
            query.eq(SysNoticeEntity::getPublished, 1)
                    .and(wrapper -> wrapper.isNull(SysNoticeEntity::getPublishTime)
                            .or()
                            .le(SysNoticeEntity::getPublishTime, TimeSupport.utcNowDateTime()));
        }
        applyCreatorScope(query, visibleCreators, SysNoticeEntity::getCreatedBy);
        return query;
    }

    private <E, V> PageResult<V> pageQuery(
            LambdaQueryWrapper<E> countQuery,
            LambdaQueryWrapper<E> listQuery,
            int page,
            int size,
            Function<LambdaQueryWrapper<E>, Long> counter,
            Function<LambdaQueryWrapper<E>, List<V>> recordsLoader,
            SFunction<E, ?> orderField,
            String direction
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        long total = counter.apply(countQuery);
        if (total == 0) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }
        int offset = (safePage - 1) * safeSize;
        if (SORT_ASC.equals(direction)) {
            listQuery.orderByAsc(orderField);
        } else {
            listQuery.orderByDesc(orderField);
        }
        listQuery.last("limit " + offset + "," + safeSize);
        return PageResult.of(total, safePage, safeSize, recordsLoader.apply(listQuery));
    }

    private SysNoticeEntity getNotice(Long id, String tenantId) {
        SysNoticeEntity entity = sysNoticeMapper.selectOne(new LambdaQueryWrapper<SysNoticeEntity>()
                .eq(SysNoticeEntity::getId, id)
                .eq(SysNoticeEntity::getTenantId, tenantId)
                .eq(SysNoticeEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("公告不存在");
        }
        if (!dataScopeService.canAccessCreatedBy(tenantId, entity.getCreatedBy())) {
            throw new BusinessException("无权访问该公告");
        }
        return entity;
    }

    private String currentScopeCacheKey() {
        return dataScopeService.currentUser()
                .map(user -> user.username() + "|" + user.dataScopeType() + "|" + user.customDeptIds().stream().sorted().toList())
                .orElse("anonymous");
    }

    private void publishNoticeNotificationIfActive(SysNoticeEntity entity, String operator, boolean alreadyActivePublished) {
        if (entity == null || alreadyActivePublished || !activePublished(entity)) {
            return;
        }
        notificationScenarioPublisher.systemNoticePublished(
                entity.getTenantId(),
                entity.getId(),
                entity.getNoticeTitle(),
                entity.getNoticeContent(),
                operator
        );
    }

    private boolean activePublished(SysNoticeEntity entity) {
        if (entity == null || entity.getPublished() == null || entity.getPublished() != 1) {
            return false;
        }
        return entity.getPublishTime() == null || !entity.getPublishTime().isAfter(TimeSupport.utcNowDateTime());
    }

    private SFunction<SysNoticeEntity, ?> resolveNoticeSort(String sortBy) {
        if ("createdAt".equalsIgnoreCase(sortBy)) {
            return SysNoticeEntity::getCreatedAt;
        }
        if ("noticeTitle".equalsIgnoreCase(sortBy)) {
            return SysNoticeEntity::getNoticeTitle;
        }
        return SysNoticeEntity::getPublishTime;
    }

    private String resolveDirection(String sortDirection, String defaultValue) {
        return SORT_ASC.equalsIgnoreCase(sortDirection)
                ? SORT_ASC
                : SORT_DESC.equalsIgnoreCase(sortDirection) ? SORT_DESC : defaultValue;
    }

    private String workflowStatus(SysNoticeEntity entity) {
        boolean published = entity.getPublished() != null && entity.getPublished() == 1;
        if (!published) {
            return "DRAFT";
        }
        if (entity.getPublishTime() != null && entity.getPublishTime().isAfter(TimeSupport.utcNowDateTime())) {
            return "SCHEDULED";
        }
        return "PUBLISHED";
    }

    private <E> void applyCreatorScope(LambdaQueryWrapper<E> query, Optional<Set<String>> visibleCreators, SFunction<E, ?> field) {
        visibleCreators.ifPresent(usernames -> {
            if (usernames.isEmpty()) {
                query.apply("1 = 0");
                return;
            }
            query.in(field, usernames);
        });
    }
}