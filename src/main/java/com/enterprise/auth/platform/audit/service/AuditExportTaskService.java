package com.enterprise.auth.platform.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.dto.AuditExportPolicyRequest;
import com.enterprise.auth.platform.audit.model.AuditEvent;
import com.enterprise.auth.platform.audit.model.AuditQuery;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.persistence.entity.SysConfigEntity;
import com.enterprise.auth.platform.persistence.entity.SysAuditExportTaskEntity;
import com.enterprise.auth.platform.persistence.mapper.SysConfigMapper;
import com.enterprise.auth.platform.persistence.mapper.SysAuditExportTaskMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuditExportTaskService {

    private final SysAuditExportTaskMapper sysAuditExportTaskMapper;
    private final SysConfigMapper sysConfigMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.Executor auditExportExecutor;

    public AuditExportTaskService(
            SysAuditExportTaskMapper sysAuditExportTaskMapper,
            SysConfigMapper sysConfigMapper,
            AuditService auditService,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Qualifier("auditExportExecutor") java.util.concurrent.Executor auditExportExecutor
    ) {
        this.sysAuditExportTaskMapper = sysAuditExportTaskMapper;
        this.sysConfigMapper = sysConfigMapper;
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
        Map<String, Object> queryPayload = new LinkedHashMap<>();
        queryPayload.put("tenantId", query.tenantId());
        queryPayload.put("eventType", query.eventType());
        queryPayload.put("operator", query.operator());
        queryPayload.put("requestId", query.requestId());
        queryPayload.put("clientIp", query.clientIp());
        queryPayload.put("occurredFrom", query.occurredFrom() == null ? null : String.valueOf(query.occurredFrom()));
        queryPayload.put("occurredTo", query.occurredTo() == null ? null : String.valueOf(query.occurredTo()));
        entity.setQueryJson(toJson(queryPayload));
        entity.setRecordCount(0);
        sysAuditExportTaskMapper.insert(entity);
        auditService.record("AUDIT_EXPORT_TASK_CREATED", entity.getOperator(), entity.getTenantId(), Map.of(
                "taskId", entity.getId(),
                "fileName", entity.getFileName()
        ));
        Long taskId = entity.getId();
        auditExportExecutor.execute(() -> processTask(taskId, query));
        return toView(entity);
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
            throw new BusinessException("导出文件尚未生成完成");
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

    public ExportPolicy policy(String tenantId) {
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "platform";
        return new ExportPolicy(
                parsePositiveInt(loadConfigValue(resolvedTenantId, "audit.export.retention.days"), 7),
                parsePositiveInt(loadConfigValue(resolvedTenantId, "audit.export.retention.max_tasks"), 100)
        );
    }

    @Transactional
    public ExportPolicy updatePolicy(String tenantId, AuditExportPolicyRequest request) {
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "platform";
        upsertConfig(resolvedTenantId, "audit.export.retention.days", "审计导出保留天数", String.valueOf(request.retentionDays()));
        upsertConfig(resolvedTenantId, "audit.export.retention.max_tasks", "审计导出最大任务数", String.valueOf(request.maxTasks()));
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

    private String resolveTenantId(AuditQuery query) {
        if (StringUtils.hasText(query.tenantId())) {
            return query.tenantId();
        }
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
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
            case "SUCCESS", "FAILED" -> 100;
            default -> 0;
        };
        String progressStage = switch (entity.getStatus()) {
            case "PENDING" -> "等待执行";
            case "RUNNING" -> "正在生成文件";
            case "SUCCESS" -> "文件已生成";
            case "FAILED" -> "执行失败";
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
                entity.getFileName(),
                entity.getRecordCount(),
                progressPercent,
                progressStage,
                retentionExpired,
                expiresAt,
                entity.getRequestedAt() == null ? null : entity.getRequestedAt().atZone(ZoneId.systemDefault()).toInstant(),
                entity.getCompletedAt() == null ? null : entity.getCompletedAt().atZone(ZoneId.systemDefault()).toInstant(),
                entity.getErrorMessage()
        );
    }

    private String loadConfigValue(String tenantId, String key) {
        SysConfigEntity entity = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getTenantId, tenantId)
                .eq(SysConfigEntity::getConfigKey, key)
                .eq(SysConfigEntity::getDeleted, 0)
                .last("limit 1"));
        return entity == null ? null : entity.getConfigValue();
    }

    private void upsertConfig(String tenantId, String key, String name, String value) {
        SysConfigEntity entity = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getTenantId, tenantId)
                .eq(SysConfigEntity::getConfigKey, key)
                .eq(SysConfigEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            entity = new SysConfigEntity();
            entity.setTenantId(tenantId);
            entity.setConfigKey(key);
            entity.setConfigName(name);
            entity.setConfigValue(value);
            sysConfigMapper.insert(entity);
            return;
        }
        entity.setConfigName(name);
        entity.setConfigValue(value);
        sysConfigMapper.updateById(entity);
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

    @Schema(description = "审计导出任务视图")
    public record ExportTaskView(
            @Schema(description = "任务 ID") Long id,
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "发起人") String operator,
            @Schema(description = "任务状态") String status,
            @Schema(description = "文件名") String fileName,
            @Schema(description = "导出记录数") Integer recordCount,
            @Schema(description = "进度百分比") Integer progressPercent,
            @Schema(description = "进度阶段") String progressStage,
            @Schema(description = "是否已超过保留期") Boolean retentionExpired,
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
