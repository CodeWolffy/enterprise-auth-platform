package com.enterprise.auth.platform.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.dto.AuditExportPolicyRequest;
import com.enterprise.auth.platform.audit.model.AuditEvent;
import com.enterprise.auth.platform.audit.model.AuditPage;
import com.enterprise.auth.platform.audit.model.AuditQuery;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.persistence.entity.SysAuditExportPolicyEntity;
import com.enterprise.auth.platform.persistence.entity.SysAuditExportTaskEntity;
import com.enterprise.auth.platform.persistence.mapper.SysAuditExportPolicyMapper;
import com.enterprise.auth.platform.persistence.mapper.SysAuditExportTaskMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.enterprise.auth.platform.audit.model.AuditExportVO;

@Service
public class AuditExportTaskService {

    private final SysAuditExportTaskMapper sysAuditExportTaskMapper;
    private final SysAuditExportPolicyMapper sysAuditExportPolicyMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.Executor auditExportExecutor;

    public AuditExportTaskService(
            SysAuditExportTaskMapper sysAuditExportTaskMapper,
            SysAuditExportPolicyMapper sysAuditExportPolicyMapper,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Qualifier("auditExportExecutor") java.util.concurrent.Executor auditExportExecutor
    ) {
        this.sysAuditExportTaskMapper = sysAuditExportTaskMapper;
        this.sysAuditExportPolicyMapper = sysAuditExportPolicyMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.auditExportExecutor = auditExportExecutor;
    }

    @Transactional
    public ExportTaskView create(AuditQuery query) {
        auditService.validateExportQuery(query);
        SysAuditExportTaskEntity entity = new SysAuditExportTaskEntity();
        entity.setTenantId(resolveTenantId(query));
        entity.setOperator(SecuritySupport.currentOperator());
        entity.setStatus("PENDING");
        entity.setFileName("audit-export-" + System.currentTimeMillis() + ".xlsx");
        entity.setRequestedAt(LocalDateTime.now());
        entity.setQueryJson(toJson(queryPayload(query)));
        entity.setRecordCount(0);
        sysAuditExportTaskMapper.insert(entity);
        auditService.record("AUDIT_EXPORT_TASK_CREATED", entity.getOperator(), entity.getTenantId(), Map.of(
                "taskId", entity.getId(),
                "fileName", entity.getFileName()
        ));
        Long taskId = entity.getId();
        auditExportExecutor.execute(() -> processTask(taskId, query));
        return toView(entity, policy(entity.getTenantId()));
    }

    @Transactional
    public ExportTaskView retry(Long taskId) {
        SysAuditExportTaskEntity source = getTask(taskId);
        AuditQuery query = parseQuery(source);
        auditService.record("AUDIT_EXPORT_TASK_RETRIED", SecuritySupport.currentOperator(), source.getTenantId(), Map.of(
                "sourceTaskId", taskId,
                "status", source.getStatus()
        ));
        return create(query);
    }

    @Transactional
    public ExportTaskView archive(Long taskId) {
        SysAuditExportTaskEntity entity = getTask(taskId);
        if ("PENDING".equals(entity.getStatus()) || "RUNNING".equals(entity.getStatus())) {
            throw new BusinessException("Cannot archive export task while it is running");
        }
        if ("ARCHIVED".equals(entity.getStatus())) {
            return toView(entity, policy(entity.getTenantId()));
        }
        entity.setFileContent(null);
        entity.setStatus("ARCHIVED");
        if (entity.getCompletedAt() == null) {
            entity.setCompletedAt(LocalDateTime.now());
        }
        sysAuditExportTaskMapper.updateById(entity);
        auditService.record("AUDIT_EXPORT_TASK_ARCHIVED", SecuritySupport.currentOperator(), entity.getTenantId(), Map.of(
                "taskId", entity.getId(),
                "fileName", entity.getFileName()
        ));
        runGovernanceSafely(entity.getTenantId(), "archive");
        return toView(entity, policy(entity.getTenantId()));
    }

    public PageResult<ExportTaskView> page(String tenantId, String status, String operator, int page, int size) {
        ExportPolicy policy = policy(resolveTenantId(new AuditQuery(tenantId, null, null, null, null, null, null, 1, 1)));
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        LambdaQueryWrapper<SysAuditExportTaskEntity> query = new LambdaQueryWrapper<SysAuditExportTaskEntity>()
                .eq(StringUtils.hasText(tenantId), SysAuditExportTaskEntity::getTenantId, tenantId)
                .eq(StringUtils.hasText(status), SysAuditExportTaskEntity::getStatus, status)
                .like(StringUtils.hasText(operator), SysAuditExportTaskEntity::getOperator, operator)
                .orderByDesc(SysAuditExportTaskEntity::getRequestedAt)
                .orderByDesc(SysAuditExportTaskEntity::getId);
        long total = sysAuditExportTaskMapper.selectCount(query);
        if (total == 0) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }
        int offset = (safePage - 1) * safeSize;
        List<ExportTaskView> records = sysAuditExportTaskMapper.selectList(query.last("limit " + offset + "," + safeSize))
                .stream()
                .map(item -> toView(item, policy))
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    public DownloadFile download(Long taskId) {
        SysAuditExportTaskEntity entity = getTask(taskId);
        if (!"SUCCESS".equals(entity.getStatus()) || entity.getFileContent() == null) {
            throw new BusinessException("Export file is not ready or has been archived");
        }
        return new DownloadFile(entity.getFileName(), entity.getFileContent());
    }

    @Transactional
    public void deleteTask(Long taskId) {
        SysAuditExportTaskEntity entity = getTask(taskId);
        sysAuditExportTaskMapper.deleteById(taskId);
        auditService.record("AUDIT_EXPORT_TASK_DELETED", SecuritySupport.currentOperator(), entity.getTenantId(), Map.of(
                "taskId", taskId,
                "status", entity.getStatus()
        ));
    }

    @Transactional
    public int cleanup(String tenantId, String status, Instant completedBefore) {
        if (completedBefore == null) {
            throw new BusinessException("completedBefore is required for cleanup");
        }
        LambdaQueryWrapper<SysAuditExportTaskEntity> query = new LambdaQueryWrapper<SysAuditExportTaskEntity>()
                .eq(StringUtils.hasText(tenantId), SysAuditExportTaskEntity::getTenantId, tenantId)
                .eq(StringUtils.hasText(status), SysAuditExportTaskEntity::getStatus, status)
                .le(SysAuditExportTaskEntity::getCompletedAt, LocalDateTime.ofInstant(completedBefore, ZoneId.systemDefault()));
        List<SysAuditExportTaskEntity> tasks = sysAuditExportTaskMapper.selectList(query);
        if (tasks.isEmpty()) {
            return 0;
        }
        int affected = sysAuditExportTaskMapper.delete(query);
        auditService.record("AUDIT_EXPORT_TASK_CLEANED", SecuritySupport.currentOperator(), resolveTenantId(new AuditQuery(
                tenantId, null, null, null, null, null, null, 1, 1
        )), Map.of(
                "tenantId", tenantId == null ? "" : tenantId,
                "status", status == null ? "" : status,
                "completedBefore", completedBefore.toString(),
                "affected", affected
        ));
        return affected;
    }

    @Transactional
    public int archiveCompleted(String tenantId, String status, Instant completedBefore) {
        if (completedBefore == null) {
            throw new BusinessException("completedBefore is required for batch archive");
        }
        LambdaQueryWrapper<SysAuditExportTaskEntity> query = new LambdaQueryWrapper<SysAuditExportTaskEntity>()
                .eq(StringUtils.hasText(tenantId), SysAuditExportTaskEntity::getTenantId, tenantId)
                .eq(StringUtils.hasText(status), SysAuditExportTaskEntity::getStatus, status)
                .ne(SysAuditExportTaskEntity::getStatus, "ARCHIVED")
                .isNotNull(SysAuditExportTaskEntity::getCompletedAt)
                .le(SysAuditExportTaskEntity::getCompletedAt, LocalDateTime.ofInstant(completedBefore, ZoneId.systemDefault()));
        List<SysAuditExportTaskEntity> tasks = sysAuditExportTaskMapper.selectList(query);
        if (tasks.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (SysAuditExportTaskEntity entity : tasks) {
            if ("PENDING".equals(entity.getStatus()) || "RUNNING".equals(entity.getStatus())) {
                continue;
            }
            entity.setFileContent(null);
            entity.setStatus("ARCHIVED");
            if (entity.getCompletedAt() == null) {
                entity.setCompletedAt(LocalDateTime.now());
            }
            affected += sysAuditExportTaskMapper.updateById(entity);
        }
        auditService.record("AUDIT_EXPORT_TASK_BATCH_ARCHIVED", SecuritySupport.currentOperator(), resolveTenantId(new AuditQuery(
                tenantId, null, null, null, null, null, null, 1, 1
        )), Map.of(
                "tenantId", tenantId == null ? "" : tenantId,
                "status", status == null ? "" : status,
                "completedBefore", completedBefore.toString(),
                "affected", affected
        ));
        return affected;
    }

    public ExportPolicy policy(String tenantId) {
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "platform";
        SysAuditExportPolicyEntity entity = sysAuditExportPolicyMapper.selectOne(new LambdaQueryWrapper<SysAuditExportPolicyEntity>()
                .eq(SysAuditExportPolicyEntity::getTenantId, resolvedTenantId)
                .eq(SysAuditExportPolicyEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            return new ExportPolicy(7, 100);
        }
        return new ExportPolicy(
                parsePositiveInt(entity.getRetentionDays() == null ? null : String.valueOf(entity.getRetentionDays()), 7),
                parsePositiveInt(entity.getMaxTasks() == null ? null : String.valueOf(entity.getMaxTasks()), 100)
        );
    }

    @Transactional
    public ExportPolicy updatePolicy(String tenantId, AuditExportPolicyRequest request) {
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "platform";
        SysAuditExportPolicyEntity entity = sysAuditExportPolicyMapper.selectOne(new LambdaQueryWrapper<SysAuditExportPolicyEntity>()
                .eq(SysAuditExportPolicyEntity::getTenantId, resolvedTenantId)
                .eq(SysAuditExportPolicyEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            entity = new SysAuditExportPolicyEntity();
            entity.setTenantId(resolvedTenantId);
            entity.setRetentionDays(request.retentionDays());
            entity.setMaxTasks(request.maxTasks());
            sysAuditExportPolicyMapper.insert(entity);
        } else {
            entity.setRetentionDays(request.retentionDays());
            entity.setMaxTasks(request.maxTasks());
            sysAuditExportPolicyMapper.updateById(entity);
        }
        auditService.record("AUDIT_EXPORT_POLICY_UPDATED", SecuritySupport.currentOperator(), resolvedTenantId, Map.of(
                "retentionDays", request.retentionDays(),
                "maxTasks", request.maxTasks()
        ));
        runGovernanceSafely(resolvedTenantId, "policy-update");
        return policy(resolvedTenantId);
    }

    @Transactional
    public GovernanceResult governance(String tenantId, boolean dryRun) {
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "platform";
        ExportPolicy currentPolicy = policy(resolvedTenantId);
        int retentionDays = Math.max(1, currentPolicy.retentionDays());
        int maxTasks = Math.max(1, currentPolicy.maxTasks());
        LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(retentionDays);

        List<SysAuditExportTaskEntity> completedTasks = sysAuditExportTaskMapper.selectList(
                new LambdaQueryWrapper<SysAuditExportTaskEntity>()
                        .eq(SysAuditExportTaskEntity::getTenantId, resolvedTenantId)
                        .isNotNull(SysAuditExportTaskEntity::getCompletedAt)
                        .ne(SysAuditExportTaskEntity::getStatus, "PENDING")
                        .ne(SysAuditExportTaskEntity::getStatus, "RUNNING")
                        .orderByAsc(SysAuditExportTaskEntity::getCompletedAt)
                        .orderByAsc(SysAuditExportTaskEntity::getId)
        );
        if (completedTasks.isEmpty()) {
            return new GovernanceResult(
                    resolvedTenantId, true, dryRun, retentionDays, maxTasks, retentionCutoff.atZone(ZoneId.systemDefault()).toInstant(),
                    0, 0, 0, 0, 0, List.of(), List.of()
            );
        }

        List<Long> archiveIds = new ArrayList<>();
        List<Long> deleteIds = new ArrayList<>();
        Set<Long> archiveSeen = new HashSet<>();
        Set<Long> deleteSeen = new HashSet<>();
        List<SysAuditExportTaskEntity> withinRetention = new ArrayList<>();

        for (SysAuditExportTaskEntity task : completedTasks) {
            if (task.getCompletedAt() != null && !task.getCompletedAt().isAfter(retentionCutoff)) {
                if (!"ARCHIVED".equals(task.getStatus()) && archiveSeen.add(task.getId())) {
                    archiveIds.add(task.getId());
                }
                if (deleteSeen.add(task.getId())) {
                    deleteIds.add(task.getId());
                }
            } else {
                withinRetention.add(task);
            }
        }

        if (withinRetention.size() > maxTasks) {
            List<SysAuditExportTaskEntity> ordered = withinRetention.stream()
                    .sorted(Comparator.comparing(SysAuditExportTaskEntity::getCompletedAt).reversed()
                            .thenComparing(SysAuditExportTaskEntity::getId, Comparator.reverseOrder()))
                    .toList();
            List<SysAuditExportTaskEntity> overflow = ordered.subList(maxTasks, ordered.size());
            for (SysAuditExportTaskEntity task : overflow) {
                if (!"ARCHIVED".equals(task.getStatus()) && archiveSeen.add(task.getId())) {
                    archiveIds.add(task.getId());
                }
                if (deleteSeen.add(task.getId())) {
                    deleteIds.add(task.getId());
                }
            }
        }

        int archivedCount = 0;
        int deletedCount = 0;
        if (!dryRun) {
            for (Long archiveId : archiveIds) {
                SysAuditExportTaskEntity task = sysAuditExportTaskMapper.selectById(archiveId);
                if (task == null || "ARCHIVED".equals(task.getStatus())) {
                    continue;
                }
                task.setFileContent(null);
                task.setStatus("ARCHIVED");
                if (task.getCompletedAt() == null) {
                    task.setCompletedAt(LocalDateTime.now());
                }
                archivedCount += sysAuditExportTaskMapper.updateById(task);
            }
            for (Long deleteId : deleteIds) {
                deletedCount += sysAuditExportTaskMapper.deleteById(deleteId);
            }
            auditService.record("AUDIT_EXPORT_POLICY_GOVERNED", SecuritySupport.currentOperator(), resolvedTenantId, Map.of(
                    "retentionDays", retentionDays,
                    "maxTasks", maxTasks,
                    "scanned", completedTasks.size(),
                    "archived", archivedCount,
                    "deleted", deletedCount
            ));
        } else {
            archivedCount = archiveIds.size();
            deletedCount = deleteIds.size();
        }

        return new GovernanceResult(
                resolvedTenantId,
                false,
                dryRun,
                retentionDays,
                maxTasks,
                retentionCutoff.atZone(ZoneId.systemDefault()).toInstant(),
                completedTasks.size(),
                archiveIds.size(),
                deleteIds.size(),
                archivedCount,
                deletedCount,
                archiveIds.stream().limit(20).toList(),
                deleteIds.stream().limit(20).toList()
        );
    }

    public void processTask(Long taskId, AuditQuery query) {
        SysAuditExportTaskEntity entity;
        try {
            entity = getTask(taskId);
        } catch (BusinessException ex) {
            return;
        }
        try {
            entity.setStatus("RUNNING");
            sysAuditExportTaskMapper.updateById(entity);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            long totalRead = 0;
            try (ExcelWriter excelWriter = EasyExcel.write(outputStream, AuditExportVO.class).build()) {
                WriteSheet writeSheet = EasyExcel.writerSheet("Audit Logs").build();
                int pageSize = 10000;
                long total = -1;
                int currentPage = 1;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

                while (total == -1 || totalRead < total) {
                    AuditQuery pageQuery = new AuditQuery(
                            query.tenantId(), query.eventType(), query.operator(), query.requestId(), query.clientIp(),
                            query.occurredFrom(), query.occurredTo(), currentPage, pageSize
                    );
                    AuditPage pageResult = auditService.query(pageQuery);
                    if (total == -1) total = pageResult.total();

                    List<AuditExportVO> voList = pageResult.records().stream().map(event -> new AuditExportVO(
                            event.type(),
                            event.operator(),
                            event.tenantId(),
                            event.requestId(),
                            event.clientIp(),
                            event.occurredAt() != null ? formatter.format(event.occurredAt()) : "",
                            toJson(event.details())
                    )).toList();

                    excelWriter.write(voList, writeSheet);
                    totalRead += pageResult.records().size();

                    if (pageResult.records().isEmpty()) {
                        break;
                    }
                    currentPage++;
                }
            }

            entity.setRecordCount((int) totalRead);
            entity.setFileContent(outputStream.toByteArray());
            entity.setStatus("SUCCESS");
            entity.setCompletedAt(LocalDateTime.now());
            sysAuditExportTaskMapper.updateById(entity);
            auditService.record("AUDIT_EXPORT_TASK_COMPLETED", entity.getOperator(), entity.getTenantId(), Map.of(
                    "taskId", entity.getId(),
                    "recordCount", entity.getRecordCount()
            ));
            runGovernanceSafely(entity.getTenantId(), "task-completed");
        } catch (Exception ex) {
            entity.setStatus("FAILED");
            entity.setErrorMessage(ex.getMessage());
            entity.setCompletedAt(LocalDateTime.now());
            sysAuditExportTaskMapper.updateById(entity);
            auditService.record("AUDIT_EXPORT_TASK_FAILED", entity.getOperator(), entity.getTenantId(), Map.of(
                    "taskId", entity.getId(),
                    "errorMessage", ex.getMessage() == null ? "" : ex.getMessage()
            ));
            runGovernanceSafely(entity.getTenantId(), "task-failed");
        }
    }

    private void runGovernanceSafely(String tenantId, String trigger) {
        try {
            governance(tenantId, false);
        } catch (Exception ex) {
            String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "platform";
            auditService.record("AUDIT_EXPORT_POLICY_GOVERNANCE_SKIPPED", SecuritySupport.currentOperator(), resolvedTenantId, Map.of(
                    "trigger", trigger,
                    "reason", ex.getMessage() == null ? "" : ex.getMessage()
            ));
        }
    }

    private SysAuditExportTaskEntity getTask(Long taskId) {
        SysAuditExportTaskEntity entity = sysAuditExportTaskMapper.selectById(taskId);
        if (entity == null) {
            throw new BusinessException("Export task not found");
        }
        return entity;
    }

    private AuditQuery parseQuery(SysAuditExportTaskEntity entity) {
        try {
            Map<String, Object> payload = objectMapper.readValue(entity.getQueryJson(), Map.class);
            return new AuditQuery(
                    stringValue(payload.get("tenantId")),
                    stringValue(payload.get("eventType")),
                    stringValue(payload.get("operator")),
                    stringValue(payload.get("requestId")),
                    stringValue(payload.get("clientIp")),
                    parseInstant(payload.get("occurredFrom")),
                    parseInstant(payload.get("occurredTo")),
                    1,
                    2000
            );
        } catch (Exception ex) {
            throw new BusinessException("閻庣數鍘ч崵顓熺鐠囨彃顫ら柡灞诲劥椤曟寮堕垾鍙夘偨閻熸瑱绲鹃悗鑺ュ緞鏉堫偉袝");
        }
    }

    private String resolveTenantId(AuditQuery query) {
        if (StringUtils.hasText(query.tenantId())) {
            return query.tenantId();
        }
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    private Map<String, Object> queryPayload(AuditQuery query) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", query.tenantId());
        payload.put("eventType", query.eventType());
        payload.put("operator", query.operator());
        payload.put("requestId", query.requestId());
        payload.put("clientIp", query.clientIp());
        payload.put("occurredFrom", query.occurredFrom() == null ? null : String.valueOf(query.occurredFrom()));
        payload.put("occurredTo", query.occurredTo() == null ? null : String.valueOf(query.occurredTo()));
        return payload;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private ExportTaskView toView(SysAuditExportTaskEntity entity, ExportPolicy policy) {
        int progressPercent = switch (entity.getStatus()) {
            case "PENDING" -> 10;
            case "RUNNING" -> 60;
            case "SUCCESS", "FAILED", "ARCHIVED" -> 100;
            default -> 0;
        };
        String progressStage = switch (entity.getStatus()) {
            case "PENDING" -> "Waiting";
            case "RUNNING" -> "Generating file";
            case "SUCCESS" -> "Completed";
            case "FAILED" -> "Failed";
            case "ARCHIVED" -> "Archived";
            default -> "Unknown";
        };
        Instant expiresAt = entity.getCompletedAt() == null
                ? null
                : entity.getCompletedAt().plusDays(policy.retentionDays()).atZone(ZoneId.systemDefault()).toInstant();
        boolean retentionExpired = expiresAt != null && expiresAt.isBefore(Instant.now());
        return new ExportTaskView(
                entity.getId(),
                entity.getTenantId(),
                entity.getOperator(),
                entity.getStatus(),
                "ARCHIVED".equals(entity.getStatus()),
                !"PENDING".equals(entity.getStatus()) && !"RUNNING".equals(entity.getStatus()) && !"ARCHIVED".equals(entity.getStatus()),
                entity.getFileName(),
                entity.getRecordCount(),
                progressPercent,
                progressStage,
                retentionExpired,
                buildRetentionSummary(entity, policy, expiresAt, retentionExpired),
                expiresAt,
                entity.getRequestedAt() == null ? null : entity.getRequestedAt().atZone(ZoneId.systemDefault()).toInstant(),
                entity.getCompletedAt() == null ? null : entity.getCompletedAt().atZone(ZoneId.systemDefault()).toInstant(),
                entity.getErrorMessage()
        );
    }

    private String buildRetentionSummary(
            SysAuditExportTaskEntity entity,
            ExportPolicy policy,
            Instant expiresAt,
            boolean retentionExpired
    ) {
        return switch (entity.getStatus()) {
            case "PENDING" -> "Task is pending. Export file will be retained for " + policy.retentionDays() + " days after completion.";
            case "RUNNING" -> "Task is running. Export file will be retained for " + policy.retentionDays() + " days after completion.";
            case "FAILED" -> expiresAt == null
                    ? "Task failed. You can adjust the query and retry export."
                    : "Failed task metadata is retained until " + expiresAt + ".";
            case "ARCHIVED" -> "Export result has been archived. Metadata is retained for audit tracking.";
            case "SUCCESS" -> retentionExpired
                    ? "Export file has exceeded retention. Archive or clean up promptly."
                    : "Export file is retained until " + expiresAt + ".";
            default -> "Manage this task according to current retention policy.";
        };
    }

    private int parsePositiveInt(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Math.max(Integer.parseInt(value), 1);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Instant parseInstant(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return Instant.parse(String.valueOf(value));
    }

    @Schema(description = "Audit export task view")
    public record ExportTaskView(
            @Schema(description = "Task ID") Long id,
            @Schema(description = "Tenant ID") String tenantId,
            @Schema(description = "Operator") String operator,
            @Schema(description = "Task status") String status,
            @Schema(description = "Whether archived") Boolean archived,
            @Schema(description = "Whether archivable") Boolean archivable,
            @Schema(description = "File name") String fileName,
            @Schema(description = "Record count") Integer recordCount,
            @Schema(description = "Progress percentage") Integer progressPercent,
            @Schema(description = "Progress stage") String progressStage,
            @Schema(description = "Whether retention expired") Boolean retentionExpired,
            @Schema(description = "Retention summary") String retentionSummary,
            @Schema(description = "Expires at") Instant expiresAt,
            @Schema(description = "Requested at") Instant requestedAt,
            @Schema(description = "Completed at") Instant completedAt,
            @Schema(description = "Error message") String errorMessage
    ) {
    }

    public record DownloadFile(String fileName, byte[] content) {
    }

    @Schema(description = "Audit export retention policy")
    public record ExportPolicy(
            @Schema(description = "Retention days") Integer retentionDays,
            @Schema(description = "Maximum tasks") Integer maxTasks
    ) {
    }

    @Schema(description = "Audit export governance execution result")
    public record GovernanceResult(
            @Schema(description = "Tenant ID") String tenantId,
            @Schema(description = "Whether no task to process") Boolean noData,
            @Schema(description = "Whether dry run") Boolean dryRun,
            @Schema(description = "Retention days") Integer retentionDays,
            @Schema(description = "Maximum tasks") Integer maxTasks,
            @Schema(description = "Retention cutoff") Instant retentionCutoff,
            @Schema(description = "Scanned completed tasks") Integer scannedTasks,
            @Schema(description = "Planned archive count") Integer plannedArchiveCount,
            @Schema(description = "Planned delete count") Integer plannedDeleteCount,
            @Schema(description = "Archived count") Integer archivedCount,
            @Schema(description = "Deleted count") Integer deletedCount,
            @Schema(description = "Archived task ID samples") List<Long> archivedSampleIds,
            @Schema(description = "Deleted task ID samples") List<Long> deletedSampleIds
    ) {
    }
}
