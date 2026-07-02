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

    private static List<String> scanFiles(String layer, Pattern violationPattern) throws IOException {
        try (Stream<Path> files = Files.walk(MODULES_ROOT)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        Path parent = path.getParent();
                        return parent != null && layer.equals(parent.getFileName().toString());
                    })
                    .flatMap(path -> findViolations(path, violationPattern).stream())
                    .toList();
        }
    }

    private static List<String> findViolations(Path path, Pattern violationPattern) {
        String currentModule = currentModule(path);
        if (currentModule.isBlank()) {
            return List.of();
        }
        try {
            return Files.readAllLines(path).stream()
                    .map(line -> violation(path, currentModule, line, violationPattern))
                    .filter(message -> !message.isBlank())
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
