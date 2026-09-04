package com.enterprise.auth.platform.modules.user.api;

import org.springframework.web.multipart.MultipartFile;

/** User-owned contract for avatar storage and public URL resolution. */
public interface UserAvatarFilePort {

    UploadedAvatar uploadCurrentUserAvatar(MultipartFile file);

    String publicUrl(String fileKey);

    record UploadedAvatar(String fileKey) {
    }
}
