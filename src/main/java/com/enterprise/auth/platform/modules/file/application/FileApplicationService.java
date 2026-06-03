package com.enterprise.auth.platform.modules.file.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.file.domain.FileVisibility;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import com.enterprise.auth.platform.modules.file.infrastructure.mapper.SysStorageFileMapper;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileApplicationService {

    private static final DateTimeFormatter PATH_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FileStorageProperties properties;
    private final ObjectStorageService objectStorageService;
    private final SysStorageFileMapper storageFileMapper;
    private final CurrentUserService currentUserService;
    private final PlatformAdminSupport platformAdminSupport;
    private final AuditService auditService;

    public FileApplicationService(
            FileStorageProperties properties,
            ObjectStorageService objectStorageService,
            SysStorageFileMapper storageFileMapper,
            CurrentUserService currentUserService,
            PlatformAdminSupport platformAdminSupport,
            AuditService auditService
    ) {
        this.properties = properties;
        this.objectStorageService = objectStorageService;
        this.storageFileMapper = storageFileMapper;
        this.currentUserService = currentUserService;
        this.platformAdminSupport = platformAdminSupport;
        this.auditService = auditService;
    }

    @Transactional
    public FileMetadataView upload(MultipartFile file, FileVisibility visibility) {
        UserAccount user = currentUserService.requireCurrentUser();
        validateUpload(file);
        FileVisibility resolvedVisibility = visibility == null ? FileVisibility.OWNER : visibility;
        assertVisibilityWritable(user, resolvedVisibility);

        String tenantId = currentTenantId(user);
        String fileKey = newFileKey();
        String objectKey = buildObjectKey(tenantId, fileKey, file.getOriginalFilename());
        String contentType = resolveContentType(file);
        ObjectStorageService.StoredObject storedObject;
        try (InputStream inputStream = file.getInputStream()) {
            storedObject = objectStorageService.put(objectKey, inputStream, file.getSize(), contentType, file.getOriginalFilename());
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_ERROR", "文件上传失败");
        }

        SysStorageFileEntity entity = new SysStorageFileEntity();
        entity.setTenantId(tenantId);
        entity.setFileKey(fileKey);
        entity.setOriginalName(safeOriginalName(file.getOriginalFilename()));
        entity.setContentType(contentType);
        entity.setFileSize(file.getSize());
        entity.setStorageType(storedObject.storageType());
        entity.setBucketName(storedObject.bucketName());
        entity.setObjectKey(storedObject.objectKey());
        entity.setEtag(storedObject.etag());
        entity.setVisibility(resolvedVisibility.name());
        entity.setOwnerUserId(user.id());
        storageFileMapper.insert(entity);

        auditService.record("FILE_UPLOADED", user.username(), tenantId, Map.of(
                "fileKey", fileKey,
                "visibility", resolvedVisibility.name(),
                "contentType", contentType,
                "size", file.getSize()
        ));
        return toView(entity);
    }

    public FileMetadataView metadata(String fileKey) {
        UserAccount user = currentUserService.requireCurrentUser();
        SysStorageFileEntity entity = loadByFileKey(fileKey);
        assertReadable(entity, user);
        return toView(entity);
    }

    public FileDownloadResult download(String fileKey) {
        UserAccount user = currentUserService.requireCurrentUser();
        SysStorageFileEntity entity = loadByFileKey(fileKey);
        assertReadable(entity, user);
        return toDownload(entity);
    }

    public FileDownloadResult publicDownload(String fileKey) {
        SysStorageFileEntity entity = loadByFileKey(fileKey);
        if (FileVisibility.from(entity.getVisibility()) != FileVisibility.PUBLIC) {
            throw new BusinessException("ACCESS_DENIED", "无权访问文件");
        }
        return toDownload(entity);
    }

    private FileDownloadResult toDownload(SysStorageFileEntity entity) {
        InputStream stream = objectStorageService.get(entity.getBucketName(), entity.getObjectKey());
        return new FileDownloadResult(
                entity.getFileKey(),
                entity.getOriginalName(),
                entity.getContentType(),
                entity.getFileSize(),
                stream
        );
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "文件不能为空");
        }
        long maxSize = properties.resolvedMaxSizeBytes();
        if (file.getSize() > maxSize) {
            throw new BusinessException("VALIDATION_ERROR", "文件大小超出限制");
        }
        String contentType = resolveContentType(file);
        List<String> allowedTypes = properties.resolvedAllowedTypes();
        if (!allowedTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("VALIDATION_ERROR", "不支持的文件类型");
        }
    }

    private void assertVisibilityWritable(UserAccount user, FileVisibility visibility) {
        if (visibility == FileVisibility.PRIVATE) {
            throw new BusinessException("VALIDATION_ERROR", "PRIVATE 文件需绑定业务授权后创建");
        }
        if (visibility == FileVisibility.PUBLIC && !hasFileWrite(user)) {
            throw new BusinessException("ACCESS_DENIED", "无权创建公开文件");
        }
    }

    private void assertReadable(SysStorageFileEntity entity, UserAccount user) {
        FileVisibility visibility = FileVisibility.from(entity.getVisibility());
        String activeTenantId = currentTenantId(user);
        switch (visibility) {
            case PUBLIC -> {
            }
            case TENANT -> {
                if (!entity.getTenantId().equals(activeTenantId) && !platformAdminSupport.isPlatformSuperAdmin(user)) {
                    throw new BusinessException("ACCESS_DENIED", "无权访问文件");
                }
            }
            case OWNER -> {
                boolean owner = entity.getOwnerUserId() != null && entity.getOwnerUserId().equals(user.id());
                boolean sameTenantAdmin = entity.getTenantId().equals(activeTenantId) && hasFileRead(user);
                boolean platformAdmin = platformAdminSupport.isPlatformSuperAdmin(user);
                if (!owner && !sameTenantAdmin && !platformAdmin) {
                    throw new BusinessException("ACCESS_DENIED", "无权访问文件");
                }
            }
            case PRIVATE -> throw new BusinessException("ACCESS_DENIED", "该文件需业务授权访问");
        }
    }

    private SysStorageFileEntity loadByFileKey(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            throw new BusinessException("VALIDATION_ERROR", "文件键不能为空");
        }
        SysStorageFileEntity entity = storageFileMapper.selectByFileKeyIgnoreTenant(fileKey.trim());
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "文件不存在");
        }
        return entity;
    }

    private FileMetadataView toView(SysStorageFileEntity entity) {
        return new FileMetadataView(
                entity.getFileKey(),
                entity.getOriginalName(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getStorageType(),
                entity.getVisibility(),
                entity.getTenantId(),
                entity.getOwnerUserId(),
                TimeSupport.toEpochMilli(entity.getCreatedAt())
        );
    }

    private String resolveContentType(MultipartFile file) {
        String declaredType = file.getContentType();
        if (StringUtils.hasText(declaredType)) {
            return declaredType.trim().toLowerCase(Locale.ROOT);
        }
        String guessedType = URLConnection.guessContentTypeFromName(file.getOriginalFilename());
        if (StringUtils.hasText(guessedType)) {
            return guessedType.trim().toLowerCase(Locale.ROOT);
        }
        return "application/octet-stream";
    }

    private String currentTenantId(UserAccount user) {
        String activeTenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(activeTenantId)) {
            return activeTenantId;
        }
        return StringUtils.hasText(user.tenantId()) ? user.tenantId() : "platform";
    }

    private boolean hasFileRead(UserAccount user) {
        return user.permissions().contains(PermissionCodes.FILE_READ) || user.permissions().contains(PermissionCodes.FILE_WRITE);
    }

    private boolean hasFileWrite(UserAccount user) {
        return user.permissions().contains(PermissionCodes.FILE_WRITE);
    }

    private String newFileKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "file_" + HexFormat.of().formatHex(bytes);
    }

    private String buildObjectKey(String tenantId, String fileKey, String originalFilename) {
        String extension = safeExtension(originalFilename);
        return String.join("/", "tenant", tenantId, PATH_DATE.format(TimeSupport.utcNowDateTime()), fileKey + extension);
    }

    private String safeExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String name = Path.of(originalFilename).getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String extension = name.substring(dot).toLowerCase(Locale.ROOT);
        return extension.length() > 16 ? "" : extension;
    }

    private String safeOriginalName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "file";
        }
        String name = Path.of(originalFilename).getFileName().toString();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}