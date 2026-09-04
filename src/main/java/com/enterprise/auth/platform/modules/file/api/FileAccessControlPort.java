package com.enterprise.auth.platform.modules.file.api;

import java.util.Set;

/** File-owned contract for the authenticated actor needed by file policies. */
public interface FileAccessControlPort {

    FileActor requireCurrentUser();

    record FileActor(
            Long id,
            String tenantId,
            Set<String> permissions,
            boolean platformSuperAdmin
    ) {
        public FileActor {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }
}
