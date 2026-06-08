package com.enterprise.auth.platform.modules.system.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;

public final class SystemViewModels {

    private SystemViewModels() {
    }

    @Schema(description = "字典类型视图")
    public record DictView(
            @Schema(description = "字典 ID") Long id,
            @Schema(description = "字典类型") String dictType,
            @Schema(description = "字典分类") String category,
            @Schema(description = "兼容字段：字典编码") String dictCode,
            @Schema(description = "兼容字段：字典值") String dictValue,
            @Schema(description = "字典类型说明") String description,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "备注") String remarks,
            @Schema(description = "字典值数量") Long valueCount,
            @Schema(description = "更新时间") Long updatedAt,
            @Schema(description = "创建人") String createdBy
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "字典详情视图")
    public record DictDetailView(
            @Schema(description = "字典类型") DictView dict,
            @Schema(description = "字典值列表") List<DictValueView> values
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "字典值视图（二级模型）")
    public record DictValueView(
            @Schema(description = "字典值 ID") Long id,
            @Schema(description = "关联字典 ID") Long dictId,
            @Schema(description = "字典类型") String dictType,
            @Schema(description = "字典标签") String dictLabel,
            @Schema(description = "字典键值") String dictValue,
            @Schema(description = "排序") Integer sort,
            @Schema(description = "回显样式") String showClass,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "备注") String remarks,
            @Schema(description = "更新时间") Long updatedAt
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "参数项视图")
    public record ConfigView(
            @Schema(description = "参数 ID") Long id,
            @Schema(description = "参数键") String configKey,
            @Schema(description = "参数分类") String category,
            @Schema(description = "参数名称") String configName,
            @Schema(description = "参数值") String configValue,
            @Schema(description = "创建人") String createdBy
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "公告视图")
    public record NoticeView(
            @Schema(description = "公告 ID") Long id,
            @Schema(description = "公告标题") String noticeTitle,
            @Schema(description = "公告内容") String noticeContent,
            @Schema(description = "是否发布") boolean published,
            @Schema(description = "发布时间") Long publishTime,
            @Schema(description = "工作流状态") String workflowStatus,
            @Schema(description = "创建人") String createdBy
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "系统分类选项")
    public record CategoryOption(
            @Schema(description = "分类编码") String code,
            @Schema(description = "分类名称") String name,
            @Schema(description = "匹配规则") List<String> matchers
    ) implements Serializable {
        private static final long serialVersionUID = 1L;

        public boolean matches(String rawKey) {
            if (rawKey == null || rawKey.isBlank()) {
                return false;
            }
            for (String matcher : matchers) {
                if (matcher == null || matcher.isBlank()) {
                    continue;
                }
                if (matcher.endsWith("*")) {
                    if (rawKey.startsWith(matcher.substring(0, matcher.length() - 1))) {
                        return true;
                    }
                } else if (rawKey.equals(matcher)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Schema(description = "分类配置分析")
    public record CategoryAnalysis(
            @Schema(description = "分类编码") String code,
            @Schema(description = "分类名称") String name,
            @Schema(description = "目标类型") String targetType,
            @Schema(description = "匹配规则") List<String> matchers,
            @Schema(description = "引用数量") Integer referenceCount,
            @Schema(description = "引用样例") List<String> sampleReferences,
            @Schema(description = "最近审计记录") List<CategoryAuditView> recentAudits,
            @Schema(description = "七日趋势") List<CategoryTrendPoint> trend
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "分类配置审计记录")
    public record CategoryAuditView(
            @Schema(description = "事件类型") String eventType,
            @Schema(description = "操作人") String operator,
            @Schema(description = "发生时间") Long occurredAt,
            @Schema(description = "审计负载") String payloadJson
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Schema(description = "分类配置趋势点")
    public record CategoryTrendPoint(
            @Schema(description = "日期") String date,
            @Schema(description = "次数") Integer count
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}