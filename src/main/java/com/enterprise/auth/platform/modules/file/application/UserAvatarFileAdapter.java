package com.enterprise.auth.platform.modules.file.application;

import com.enterprise.auth.platform.modules.user.api.UserAvatarFilePort;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Exposes avatar file operations through the user-owned contract. */
@Component
public final class UserAvatarFileAdapter implements UserAvatarFilePort {

    private final FileApplicationService fileApplicationService;

    public UserAvatarFileAdapter(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @Override
    public UploadedAvatar uploadCurrentUserAvatar(MultipartFile file) {
        FileMetadataView uploaded = fileApplicationService.uploadCurrentUserAvatar(file);
        return new UploadedAvatar(uploaded.fileKey());
    }

    @Override
    public String publicUrl(String fileKey) {
        return fileApplicationService.publicUrl(fileKey);
    }
}
