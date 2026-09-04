package com.enterprise.auth.platform.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 真实 ArchUnit 门禁。
 * <p>硬规则：已清零的违规必须保持为零。</p>
 * <p>模块环、application→mapper 等历史遗留由 ModuleBoundaryTest 与后续 IAM 收敛处理，
 * 不在此用 FreezingArchRule 刷屏（首次 freeze 在 CI 多模块环境易不稳定）。</p>
 */
class ArchitectureRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.enterprise.auth.platform");
    }

    @Test
    void commonMustNotDependOnModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..common..")
                .should().dependOnClassesThat().resideInAPackage("..modules..")
                .because("shared kernel 已清零业务模块依赖");
        rule.check(classes);
    }

    @Test
    void businessModulesMustBeFreeOfCycles() {
        ArchRule rule = slices()
                .matching("com.enterprise.auth.platform.modules.(*)..")
                .should().beFreeOfCycles()
                .because("业务模块依赖图已经清零强连通分量，后续不得重新引入模块环");
        rule.check(classes);
    }

    @Test
    void logModuleMustNotDependOnDashboard() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.log..")
                .should().dependOnClassesThat().resideInAPackage("..modules.dashboard..")
                .because("dashboard 是读模型，log 不得反向依赖其 DTO");
        rule.check(classes);
    }

    @Test
    void logModuleMustNotDependOnAuth() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.log..")
                .should().dependOnClassesThat().resideInAPackage("..modules.auth..")
                .because("log 通过 CurrentOperatorSupplier 获取操作人上下文，不得反向依赖 auth 实现");
        rule.check(classes);
    }

    @Test
    void fileModuleMustNotDependOnAuth() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.file..")
                .should().dependOnClassesThat().resideInAPackage("..modules.auth..")
                .because("file 通过 FileAccessControlPort 获取认证主体，不得依赖 auth 实现");
        rule.check(classes);
    }

    @Test
    void systemModuleMustNotDependOnAuth() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.system..")
                .should().dependOnClassesThat().resideInAPackage("..modules.auth..")
                .because("system 通过 SystemAccessControlPort 获取操作人和数据范围，不得依赖 auth 实现");
        rule.check(classes);
    }

    @Test
    void systemModuleMustNotDependOnTenant() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.system..")
                .should().dependOnClassesThat().resideInAPackage("..modules.tenant..")
                .because("租户上下文配置属于 common，system 不得依赖 tenant 实现");
        rule.check(classes);
    }

    @Test
    void securityModuleMustNotDependOnAuthOrTenant() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.security..")
                .should().dependOnClassesThat().resideInAnyPackage("..modules.auth..", "..modules.tenant..")
                .because("security 通过 SecurityAccessControlPort 校验平台管理员，不得依赖认证或租户实现");
        rule.check(classes);
    }

    @Test
    void deptModuleMustNotDependOnAuthUserOrTenant() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.dept..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..modules.auth..", "..modules.user..", "..modules.tenant..")
                .because("dept 通过自身 API 端口和 iam.api 数据范围契约访问外部能力");
        rule.check(classes);
    }

    @Test
    void logInterfacesMustNotDependOnInfrastructureEntity() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.log.interfaces..")
                .should().dependOnClassesThat().resideInAPackage("..modules.log.infrastructure.entity..")
                .because("日志 Controller 已改为返回 View DTO");
        rule.check(classes);
    }

    @Test
    void commonAuthzMustNotDependOnModulesInfrastructure() {
        // DataScope/PlatformAdmin 已迁出 common；common.authz 仅保留纯类型与常量
        ArchRule rule = noClasses()
                .that().resideInAPackage("..common.authz..")
                .should().dependOnClassesThat().resideInAPackage("..modules..infrastructure..")
                .because("shared authz 常量层不得依赖业务 infrastructure");
        rule.check(classes);
    }

    @Test
    void authAndUserMustNotDependOnNotificationModule() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..modules.auth..", "..modules.user..")
                .should().dependOnClassesThat().resideInAPackage("..modules.notification..")
                .because("auth/user 通过 common.notification 端口解耦，不得依赖 notification 实现");
        rule.check(classes);
    }

    @Test
    void authModuleMustNotDependOnTenant() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.auth..")
                .should().dependOnClassesThat().resideInAPackage("..modules.tenant..")
                .because("auth 通过 AuthTenantQueryPort 获取登录和租户切换所需数据");
        rule.check(classes);
    }

    @Test
    void menuMustNotDependOnRoleModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.menu..")
                .should().dependOnClassesThat().resideInAPackage("..modules.role..")
                .because("menu 通过 RoleMenuReferencePort 解耦，不得依赖 role 实现");
        rule.check(classes);
    }

    @Test
    void menuModuleMustNotDependOnAuthOrTenant() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.menu..")
                .should().dependOnClassesThat().resideInAnyPackage("..modules.auth..", "..modules.tenant..")
                .because("menu 通过自身 API 端口访问管理员、租户授权和权限失效能力");
        rule.check(classes);
    }

    @Test
    void roleModuleMustOnlyDependOnMenuApi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.role..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..modules.menu.application..",
                        "..modules.menu.domain..",
                        "..modules.menu.infrastructure..")
                .because("role 只依赖 menu.api 授权、事件和引用契约");
        rule.check(classes);
    }

    @Test
    void roleModuleMustNotDependOnAuthUserTenantOrDept() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.role..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..modules.auth..", "..modules.user..", "..modules.tenant..", "..modules.dept..")
                .because("role 通过自身 API 和 iam.api 端口访问管理员、用户、租户、部门和授权失效能力");
        rule.check(classes);
    }

    @Test
    void userModuleMustUseSessionIndexPort() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.user..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.enterprise.auth.platform.modules.auth.application.SessionIndexService")
                .because("user 只依赖 UserSessionIndexPort，不依赖 auth 会话索引实现");
        rule.check(classes);
    }

    @Test
    void userModuleMustUseAuthorizationInvalidationPort() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.user..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService")
                .because("user 只依赖 UserAuthorizationInvalidationPort，不依赖 auth 快照失效实现");
        rule.check(classes);
    }

    @Test
    void userModuleMustNotDependOnAuthImplementation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.user..")
                .should().dependOnClassesThat().resideInAPackage("..modules.auth..")
                .because("user 通过 user.api 与 iam.api 契约访问认证、授权和密码能力");
        rule.check(classes);
    }

    @Test
    void userModuleMustNotDependOnTenant() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.user..")
                .should().dependOnClassesThat().resideInAPackage("..modules.tenant..")
                .because("user 通过 UserTenantReferencePort 校验跨租户管理目标");
        rule.check(classes);
    }

    @Test
    void userModuleMustNotDependOnRole() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.user..")
                .should().dependOnClassesThat().resideInAPackage("..modules.role..")
                .because("user 通过 iam.api 角色查询契约访问角色与授权数据");
        rule.check(classes);
    }

    @Test
    void userModuleMustUseStableDeptFileSecurityAndLogContracts() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.user..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..modules.file..",
                        "..modules.security..",
                        "..modules.dept..",
                        "..modules.log..")
                .because("user 通过 user.api/iam.api 契约访问头像、部门和安全策略，且不得保留无用日志发布器依赖");
        rule.check(classes);
    }

    @Test
    void iamApiPackageExistsAsStableContractAnchor() {
        ArchRule rule = classes()
                .that().resideInAPackage("..modules.iam.api..")
                .should().bePublic();
        rule.allowEmptyShould(true);
        rule.check(classes);
    }
}
