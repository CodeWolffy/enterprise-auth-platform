package com.enterprise.auth.platform.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.dto.AuditExportPolicyRequest;
import com.enterprise.auth.platform.audit.model.AuditEvent;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
        entity.setFileName("audit-export-" + System.currentTimeMillis() + ".csv");
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
            throw new BusinessException("执行中的导出任务不允许归档");
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
            throw new BusinessException("导出文件尚未生成完成或已归档");
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
            throw new BusinessException("清理导出任务必须指定完成时间上限");
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
            throw new BusinessException("批量归档必须指定完成时间上限");
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
        return policy(resolvedTenantId);
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
            List<AuditEvent> records = auditService.export(query);
            entity.setRecordCount(records.size());
            entity.setFileContent(buildCsv(records).getBytes(StandardCharsets.UTF_8));
            entity.setStatus("SUCCESS");
            entity.setCompletedAt(LocalDateTime.now());
            sysAuditExportTaskMapper.updateById(entity);
            auditService.record("AUDIT_EXPORT_TASK_COMPLETED", entity.getOperator(), entity.getTenantId(), Map.of(
                    "taskId", entity.getId(),
                    "recordCount", records.size()
            ));
        } catch (Exception ex) {
            entity.setStatus("FAILED");
            entity.setErrorMessage(ex.getMessage());
            entity.setCompletedAt(LocalDateTime.now());
            sysAuditExportTaskMapper.updateById(entity);
            auditService.record("AUDIT_EXPORT_TASK_FAILED", entity.getOperator(), entity.getTenantId(), Map.of(
                    "taskId", entity.getId(),
                    "errorMessage", ex.getMessage() == null ? "" : ex.getMessage()
            ));
        }
    }

    private SysAuditExportTaskEntity getTask(Long taskId) {
        SysAuditExportTaskEntity entity = sysAuditExportTaskMapper.selectById(taskId);
        if (entity == null) {
            throw new BusinessException("导出任务不存在");
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
            throw new BusinessException("导出任务查询条件解析失败");
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

    private String buildCsv(List<AuditEvent> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("type,operator,tenantId,requestId,clientIp,occurredAt,details\n");
        for (AuditEvent record : records) {
            appendCell(builder, record.type());
            appendCell(builder, record.operator());
            appendCell(builder, record.tenantId());
            appendCell(builder, record.requestId());
            appendCell(builder, record.clientIp());
            appendCell(builder, String.valueOf(record.occurredAt()));
            appendCell(builder, String.valueOf(record.details()));
            builder.append('\n');
        }
        return builder.toString();
    }

    private void appendCell(StringBuilder builder, String value) {
        if (builder.charAt(builder.length() - 1) != '\n') {
            builder.append(',');
        }
        String normalized = value == null ? "" : value.replace("\"", "\"\"");
        builder.append('"').append(normalized).append('"');
    }

    private ExportTaskView toView(SysAuditExportTaskEntity entity, ExportPolicy policy) {
        int progressPercent = switch (entity.getStatus()) {
            case "PENDING" -> 10;
            case "RUNNING" -> 60;
            case "SUCCESS", "FAILED", "ARCHIVED" -> 100;
            default -> 0;
        };
        String progressStage = switch (entity.getStatus()) {
            case "PENDING" -> "等待执行";
            case "RUNNING" -> "正在生成文件";
            case "SUCCESS" -> "文件已生成";
            case "FAILED" -> "执行失败";
            case "ARCHIVED" -> "结果已归档";
            default -> "未知状态";
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
            case "PENDING" -> "任务等待执行，完成后将按 " + policy.retentionDays() + " 天保留导出结果。";
            case "RUNNING" -> "任务执行中，完成后将按 " + policy.retentionDays() + " 天保留导出结果。";
            case "FAILED" -> expiresAt == null
                    ? "任务执行失败，可调整筛选条件后重新发起导出。"
                    : "失败任务记录保留至 " + expiresAt + "，可按需清理或保留用于排障。";
            case "ARCHIVED" -> "导出结果已归档，仅保留元数据与审计轨迹；如需再次下载，请重新导出。";
            case "SUCCESS" -> retentionExpired
                    ? "导出文件已超过保留期，建议及时归档或清理任务记录。"
                    : "导出文件保留至 " + expiresAt + "，到期前可归档保留元数据，到期后建议清理。";
            default -> "请按当前导出策略管理任务记录。";
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

    @Schema(description = "审计导出任务视图")
    public record ExportTaskView(
            @Schema(description = "任务 ID") Long id,
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "发起人") String operator,
            @Schema(description = "任务状态") String status,
            @Schema(description = "是否已归档") Boolean archived,
            @Schema(description = "是否允许归档") Boolean archivable,
            @Schema(description = "文件名") String fileName,
            @Schema(description = "导出记录数") Integer recordCount,
            @Schema(description = "进度百分比") Integer progressPercent,
            @Schema(description = "进度阶段") String progressStage,
            @Schema(description = "是否已超过保留期") Boolean retentionExpired,
            @Schema(description = "保留策略提示") String retentionSummary,
            @Schema(description = "预计过期时间") Instant expiresAt,
            @Schema(description = "发起时间") Instant requestedAt,
            @Schema(description = "完成时间") Instant completedAt,
            @Schema(description = "失败原因") String errorMessage
    ) {
    }

    public record DownloadFile(String fileName, byte[] content) {
    }

    @Schema(description = "审计导出保留策略")
    public record ExportPolicy(
            @Schema(description = "导出结果保留天数") Integer retentionDays,
            @Schema(description = "单租户最多保留任务数") Integer maxTasks
    ) {
    }
}
