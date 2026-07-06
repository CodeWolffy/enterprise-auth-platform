package com.enterprise.auth.platform.modules.codegen.domain.render;

import com.enterprise.auth.platform.modules.codegen.domain.model.GeneratedFile;
import com.enterprise.auth.platform.modules.codegen.domain.model.RenderContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码生成模板渲染领域服务：根据渲染上下文产出前后端生成文件清单（纯逻辑，无 Spring/JDBC 依赖）。
 */
public class CodeRenderer {

    private final BackendCodeRenderer backend = new BackendCodeRenderer();
    private final FrontendCodeRenderer frontend = new FrontendCodeRenderer();
    private final VueViewRenderer vueViews = new VueViewRenderer(new VueSnippetRenderer());

    public List<GeneratedFile> renderFiles(RenderContext model, boolean includeBackend, boolean includeFrontend) {
        List<GeneratedFile> files = new ArrayList<>();
        if (includeBackend) {
            files.add(new GeneratedFile(backendPath(model, "infrastructure/entity", model.className() + "Entity.java"), "java", backend.renderEntity(model)));
            files.add(new GeneratedFile(backendPath(model, "infrastructure/mapper", model.className() + "Mapper.java"), "java", backend.renderMapper(model)));
            files.add(new GeneratedFile(backendPath(model, "interfaces", model.className() + "CreateRequest.java"), "java", backend.renderCreateRequest(model)));
            files.add(new GeneratedFile(backendPath(model, "interfaces", model.className() + "UpdateRequest.java"), "java", backend.renderUpdateRequest(model)));
            files.add(new GeneratedFile(backendPath(model, "interfaces", model.className() + "QueryRequest.java"), "java", backend.renderQueryRequest(model)));
            files.add(new GeneratedFile(backendPath(model, "application", model.className() + "ApplicationService.java"), "java", backend.renderService(model)));
            files.add(new GeneratedFile(backendPath(model, "interfaces", model.className() + "Controller.java"), "java", backend.renderController(model)));
        }
        if (includeFrontend) {
            String frontendModulePath = model.tableName().startsWith("sys_") ? "upms/" + model.kebabName() : model.kebabName();
            files.add(new GeneratedFile("frontend-vben/apps/web-ele/src/api/" + frontendModulePath + ".ts", "typescript", frontend.renderApi(model)));
            files.add(new GeneratedFile("frontend-vben/apps/web-ele/src/types/" + model.moduleName() + ".ts", "typescript", frontend.renderTypes(model)));
            files.add(new GeneratedFile("frontend-vben/apps/web-ele/src/views/" + frontendModulePath + "/index.vue", "vue", vueViews.renderIndexView(model)));
            files.add(new GeneratedFile("frontend-vben/apps/web-ele/src/views/" + frontendModulePath + "/form.vue", "vue", vueViews.renderFormView(model)));
        }
        return files;
    }

    private String backendPath(RenderContext model, String layer, String fileName) {
        return "backend/src/main/java/" + model.packageName().replace('.', '/') + "/modules/" + model.moduleName() + "/" + layer + "/" + fileName;
    }
}
