package com.enterprise.auth.platform.audit.model;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;

public class AuditExportVO {

    @ExcelProperty("事件类型")
    @ColumnWidth(20)
    private String type;

    @ExcelProperty("操作人")
    @ColumnWidth(15)
    private String operator;

    @ExcelProperty("租户ID")
    @ColumnWidth(20)
    private String tenantId;

    @ExcelProperty("请求ID")
    @ColumnWidth(30)
    private String requestId;

    @ExcelProperty("客户端IP")
    @ColumnWidth(20)
    private String clientIp;

    @ExcelProperty("发生时间")
    @ColumnWidth(30)
    private String occurredAt;

    @ExcelProperty("详细信息")
    @ColumnWidth(50)
    private String details;

    public AuditExportVO() {
    }

    public AuditExportVO(String type, String operator, String tenantId, String requestId, String clientIp, String occurredAt, String details) {
        this.type = type;
        this.operator = operator;
        this.tenantId = tenantId;
        this.requestId = requestId;
        this.clientIp = clientIp;
        this.occurredAt = occurredAt;
        this.details = details;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
