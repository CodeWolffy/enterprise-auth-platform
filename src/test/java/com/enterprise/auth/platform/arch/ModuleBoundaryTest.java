package com.enterprise.auth.platform.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/enterprise/auth/platform");
    private static final Path MODULES_ROOT = SOURCE_ROOT.resolve("modules");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("com\\.enterprise\\.auth\\.platform\\.modules\\.([a-z]+)\\.(domain|infrastructure)");

    @Test
    void legacyFlatPackagesAreFullyMigrated() {
        List<String> legacyPackages = List.of("controller", "service", "dao", "dto");

        List<String> remaining = legacyPackages.stream()
                .map(SOURCE_ROOT::resolve)
                .filter(Files::exists)
                .map(Path::toString)
                .toList();

        assertTrue(remaining.isEmpty(), () -> "Legacy flat packages should be removed: " + String.join(", ", remaining));
    }

    @Test
    void moduleInterfacesDoNotDependOnOtherModulesDomainOrInfrastructure() throws IOException {
        if (!Files.exists(MODULES_ROOT)) {
            return;
        }

        List<String> violations;
        try (var files = Files.walk(MODULES_ROOT)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("interfaces"))
                    .flatMap(path -> findViolations(path).stream())
                    .toList();
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    private static List<String> findViolations(Path path) {
        String currentModule = currentModule(path);
        if (currentModule.isBlank()) {
            return List.of();
        }
        try {
            return Files.readAllLines(path).stream()
                    .map(line -> violation(path, currentModule, line))
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

    private static String violation(Path path, String currentModule, String line) {
        Matcher matcher = PACKAGE_PATTERN.matcher(line);
        if (!matcher.find()) {
            return "";
        }
        String targetModule = matcher.group(1);
        if (currentModule.equals(targetModule)) {
            return "";
        }
        return path + " imports restricted package from module " + targetModule + ": " + line.trim();
    }
}