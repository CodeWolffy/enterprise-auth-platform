package com.enterprise.auth.platform.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/enterprise/auth/platform");
    private static final Path MODULES_ROOT = SOURCE_ROOT.resolve("modules");
    private static final Pattern DOMAIN_INFRA_PATTERN = Pattern.compile("com\\.enterprise\\.auth\\.platform\\.modules\\.([a-z]+)\\.(domain|infrastructure)");
    private static final Pattern INFRA_MAPPER_PATTERN = Pattern.compile("com\\.enterprise\\.auth\\.platform\\.modules\\.([a-z]+)\\.infrastructure\\.mapper");
    private static final Set<String> ALLOWED_CROSS_MODULE_INFRA_IMPORTS = Set.of(
            "com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog"
    );

    @Test
    void flatPackagesAreFullyMigrated() {
        List<String> flatPackages = List.of("controller", "service", "dao", "dto");

        List<String> remaining = flatPackages.stream()
                .map(SOURCE_ROOT::resolve)
                .filter(Files::exists)
                .map(Path::toString)
                .toList();

        assertTrue(remaining.isEmpty(), () -> "Flat packages should be removed: " + String.join(", ", remaining));
    }

    @Test
    void moduleInterfacesDoNotDependOnOtherModulesDomainOrInfrastructure() throws IOException {
        if (!Files.exists(MODULES_ROOT)) {
            return;
        }

        List<String> violations = scanFiles("interfaces", DOMAIN_INFRA_PATTERN);
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void moduleApplicationLayerDoesNotDependOnOtherModulesMapper() throws IOException {
        if (!Files.exists(MODULES_ROOT)) {
            return;
        }

        List<String> violations = scanFiles("application", INFRA_MAPPER_PATTERN);
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void workflowApplicationLayerDoesNotDependOnWorkflowInfrastructure() throws IOException {
        Path workflowApplication = MODULES_ROOT.resolve("workflow").resolve("application");
        if (!Files.exists(workflowApplication)) {
            return;
        }
        Pattern workflowInfrastructure = Pattern.compile(
                "com\\.enterprise\\.auth\\.platform\\.modules\\.workflow\\.infrastructure|com\\.baomidou");
        List<String> violations;
        try (Stream<Path> files = Files.walk(workflowApplication)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> findViolations(path, "workflow", workflowInfrastructure).stream())
                    .toList();
        }
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void catalogAggregationModuleIsRemoved() throws IOException {
        Path catalog = MODULES_ROOT.resolve("catalog");
        if (Files.notExists(catalog)) {
            return;
        }
        try (Stream<Path> files = Files.walk(catalog)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".java")),
                    "Catalog aggregation should live in the owning role/dept/tenant modules");
        }
    }

    @Test
    void commonPackageDoesNotImportModules() throws IOException {
        Path commonRoot = SOURCE_ROOT.resolve("common");
        if (!Files.exists(commonRoot)) {
            return;
        }
        Pattern modulesImport = Pattern.compile("import\\s+com\\.enterprise\\.auth\\.platform\\.modules\\.");
        List<String> violations;
        try (Stream<Path> files = Files.walk(commonRoot)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> modulesImport.matcher(line).find())
                                    .map(line -> path + " : " + line.trim());
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .toList();
        }
        assertTrue(violations.isEmpty(), () ->
                "common 不得 import modules：\n" + String.join(System.lineSeparator(), violations));
    }

    @Test
    void logModuleDoesNotImportDashboard() throws IOException {
        Path logRoot = MODULES_ROOT.resolve("log");
        if (!Files.exists(logRoot)) {
            return;
        }
        Pattern dashboardImport = Pattern.compile("import\\s+com\\.enterprise\\.auth\\.platform\\.modules\\.dashboard\\.");
        List<String> violations;
        try (Stream<Path> files = Files.walk(logRoot)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> dashboardImport.matcher(line).find())
                                    .map(line -> path + " : " + line.trim());
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .toList();
        }
        assertTrue(violations.isEmpty(), () ->
                "log 不得反向依赖 dashboard：\n" + String.join(System.lineSeparator(), violations));
    }

    @Test
    void interfacesControllersDoNotReturnInfrastructureEntity() throws IOException {
        Pattern entityImport = Pattern.compile(
                "import\\s+com\\.enterprise\\.auth\\.platform\\.modules\\.[a-z]+\\.infrastructure\\.entity\\.");
        List<String> violations;
        try (Stream<Path> files = Files.walk(MODULES_ROOT)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> isUnderLayer(path, "interfaces"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> entityImport.matcher(line).find())
                                    .map(line -> path + " : " + line.trim());
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .toList();
        }
        assertTrue(violations.isEmpty(), () ->
                "interfaces 不得 import infrastructure.entity：\n" + String.join(System.lineSeparator(), violations));
    }

    private static List<String> scanFiles(String layer, Pattern violationPattern) throws IOException {
        try (Stream<Path> files = Files.walk(MODULES_ROOT)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    // 扫描 layer 及其子包（如 interfaces/controller），避免漏检
                    .filter(path -> isUnderLayer(path, layer))
                    .flatMap(path -> findViolations(path, violationPattern).stream())
                    .toList();
        }
    }

    /** path 是否位于 modules/{module}/{layer}/... 下 */
    private static boolean isUnderLayer(Path path, String layer) {
        Path relative = MODULES_ROOT.relativize(path);
        // modules/{module}/{layer}/...
        if (relative.getNameCount() < 3) {
            return false;
        }
        return layer.equals(relative.getName(1).toString());
    }

    private static List<String> findViolations(Path path, Pattern violationPattern) {
        String currentModule = currentModule(path);
        if (currentModule.isBlank()) {
            return List.of();
        }
        try {
            return java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8).stream()
                    .map(line -> violation(path, currentModule, line, violationPattern))
                    .filter(message -> !message.isBlank())
                    .toList();
        } catch (java.nio.charset.MalformedInputException encodingEx) {
            // 回退系统默认编码，避免 Windows 历史文件阻塞门禁
            try {
                return Files.readAllLines(path).stream()
                        .map(line -> violation(path, currentModule, line, violationPattern))
                        .filter(message -> !message.isBlank())
                        .toList();
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to inspect " + path, ex);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect " + path, ex);
        }
    }

    private static List<String> findViolations(Path path, String currentModule, Pattern violationPattern) {
        try {
            return Files.readAllLines(path).stream()
                    .filter(line -> violationPattern.matcher(line).find())
                    .map(line -> path + " imports restricted workflow infrastructure: " + line.trim())
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect " + path, ex);
        }
    }

    private static String currentModule(Path path) {
        Path relative = MODULES_ROOT.relativize(path);
        if (relative.getNameCount() < 1) {
            return "";
        }
        return relative.getName(0).toString();
    }

    private static String violation(Path path, String currentModule, String line, Pattern violationPattern) {
        Matcher matcher = violationPattern.matcher(line);
        if (!matcher.find()) {
            return "";
        }
        if (ALLOWED_CROSS_MODULE_INFRA_IMPORTS.stream().anyMatch(line::contains)) {
            return "";
        }
        String targetModule = matcher.group(1);
        if (currentModule.equals(targetModule)) {
            return "";
        }
        return path + " imports restricted package from module " + targetModule + ": " + line.trim();
    }
}
