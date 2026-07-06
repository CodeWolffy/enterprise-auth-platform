package com.enterprise.auth.platform.modules.codegen.infrastructure;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.codegen.domain.model.GeneratedFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 生成产物落盘器：把生成文件写入服务端隔离目录，并做路径越界防护与覆盖保护。
 */
@Component
public class CodegenFileWriter {

    private final Path outputRoot;

    public CodegenFileWriter(@Value("${platform.codegen.output-root:target/generated-codegen}") String outputRoot) {
        this.outputRoot = Path.of(outputRoot).toAbsolutePath().normalize();
    }

    /**
     * 依序写入文件，返回相对隔离目录的路径列表（正斜杠分隔）。
     */
    public List<String> write(List<GeneratedFile> files, boolean overwrite) {
        List<String> written = new ArrayList<>();
        for (GeneratedFile file : files) {
            Path target = safeTarget(file.path());
            if (Files.exists(target) && !overwrite) {
                throw new BusinessException("CONFLICT", "生成文件已存在，请启用覆盖或调整模块名：" + file.path());
            }
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content(), StandardCharsets.UTF_8);
                written.add(outputRoot.relativize(target).toString().replace('\\', '/'));
            } catch (IOException ex) {
                throw new BusinessException("CODEGEN_WRITE_FAILED", "生成文件写入失败：" + file.path());
            }
        }
        return written;
    }

    private Path safeTarget(String relativePath) {
        Path target = outputRoot.resolve(relativePath).normalize();
        if (!target.startsWith(outputRoot)) {
            throw new BusinessException("VALIDATION_ERROR", "生成路径越界");
        }
        return target;
    }
}
