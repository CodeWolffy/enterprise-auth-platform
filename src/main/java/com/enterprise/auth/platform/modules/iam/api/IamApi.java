package com.enterprise.auth.platform.modules.iam.api;

/**
 * IAM 稳定契约包（Phase 3 起点）。
 *
 * <p>跨上下文只能依赖本包中的 DTO / port / 集成事件；Entity、Mapper、内部 Service 不得跨模块导入。</p>
 * <p>当前阶段：auth 模块内的 Principal（UserAccount/SessionPrincipal）与 DataScope 服务
 * 作为实现落点，后续逐步将只读契约下沉到本包。</p>
 */
public final class IamApi {
    private IamApi() {
    }
}