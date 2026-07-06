package com.enterprise.auth.platform.modules.codegen.infrastructure;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.codegen.domain.model.GeneratedFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

/**
 * 生成产物 ZIP 打包器：把生成文件按原路径打入内存 ZIP。
 */
@Component
public class CodegenZipBuilder {

    public byte[] build(List<GeneratedFile> files) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                for (GeneratedFile file : files) {
                    zip.putNextEntry(new ZipEntry(file.path()));
                    zip.write(file.content().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("CODEGEN_PACKAGE_FAILED", "生成产物打包失败");
        }
    }
}
