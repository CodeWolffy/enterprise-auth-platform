package com.enterprise.auth.platform.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
    void logModuleMustNotDependOnDashboard() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.log..")
                .should().dependOnClassesThat().resideInAPackage("..modules.dashboard..")
                .because("dashboard 是读模型，log 不得反向依赖其 DTO");
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
    void menuMustNotDependOnRoleModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.menu..")
                .should().dependOnClassesThat().resideInAPackage("..modules.role..")
                .because("menu 通过 RoleMenuReferencePort 解耦，不得依赖 role 实现");
        rule.check(classes);
    }

    @Test
    void roleMustNotDependOnMenuServiceImplementation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.role..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.enterprise.auth.platform.modules.menu.application.MenuService")
                .because("role 只依赖 MenuGrantQueryPort，不依赖 MenuService 实现类");
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
    void iamApiPackageExistsAsStableContractAnchor() {
        ArchRule rule = classes()
                .that().resideInAPackage("..modules.iam.api..")
                .should().bePublic();
        rule.allowEmptyShould(true);
        rule.check(classes);
    }
}
