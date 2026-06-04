package com.enterprise.auth.platform.modules.codegen.application;

import java.util.List;

public record CodegenTableDetailView(
        CodegenTableView table,
        List<CodegenColumnView> columns
) {
    public CodegenTableDetailView {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}