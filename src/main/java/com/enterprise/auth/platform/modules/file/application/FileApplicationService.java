package com.enterprise.auth.platform.modules.file.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.file.domain.FileVisibility;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import com.enterprise.auth.platform.modules.file.infrastructure.mapper.SysStorageFileMapper;
import java.io.ByteArrayInputStream;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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
        return uploadInternal(file, visibility, true, false, "FILE_UPLOADED");
    }

    @Transactional
    public FileMetadataView uploadCurrentUserAvatar(MultipartFile file) {
        return uploadInternal(file, FileVisibility.PUBLIC, false, true, "AVATAR_UPLOADED");
    }

    public PageResult<FileMetadataView> page(FileQuery query) {
        UserAccount user = currentUserService.requireCurrentUser();
        FileQuery normalized = query == null ? new FileQuery(null, null, null, null, 1, 20) : query;
        LambdaQueryWrapper<SysStorageFileEntity> wrapper = new LambdaQueryWrapper<SysStorageFileEntity>()
                .eq(SysStorageFileEntity::getDeleted, 0);
        applyReadableScope(wrapper, user);
        if (StringUtils.hasText(normalized.keyword())) {
            wrapper.like(SysStorageFileEntity::getOriginalName, normalized.keyword().trim());
        }
        if (StringUtils.hasText(normalized.contentType())) {
            wrapper.eq(SysStorageFileEntity::getContentType, normalized.contentType().trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(normalized.storageType())) {
            wrapper.eq(SysStorageFileEntity::getStorageType, normalized.storageType().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(normalized.visibility())) {
            wrapper.eq(SysStorageFileEntity::getVisibility, normalized.visibility().trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(SysStorageFileEntity::getCreatedAt).orderByDesc(SysStorageFileEntity::getId);

        Page<SysStorageFileEntity> result = storageFileMapper.selectPage(
                Page.of(normalized.normalizedPage(), normalized.normalizedSize()),
                wrapper
        );
        List<FileMetadataView> records = result.getRecords().stream()
                .map(this::toView)
                .toList();
        return PageResult.of(result.getTotal(), normalized.normalizedPage(), normalized.normalizedSize(), records);
    }

    public long countVisibleFiles() {
        UserAccount user = currentUserService.requireCurrentUser();
        LambdaQueryWrapper<SysStorageFileEntity> wrapper = new LambdaQueryWrapper<SysStorageFileEntity>()
                .eq(SysStorageFileEntity::getDeleted, 0);
        applyReadableScope(wrapper, user);
        return storageFileMapper.selectCount(wrapper);
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

    @Transactional
    public void delete(String fileKey) {
        UserAccount user = currentUserService.requireCurrentUser();
        SysStorageFileEntity entity = loadByFileKey(fileKey);
        assertDeletable(entity, user);
        objectStorageService.delete(entity.getBucketName(), entity.getObjectKey());
        storageFileMapper.deleteById(entity.getId());
        auditService.record("FILE_DELETED", user.username(), entity.getTenantId(), Map.of(
                "fileKey", entity.getFileKey(),
                "visibility", entity.getVisibility(),
                "storageType", entity.getStorageType()
        ));
    }

    public String publicUrl(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            return null;
        }
        SysStorageFileEntity entity = storageFileMapper.selectByFileKeyIgnoreTenant(fileKey.trim());
        if (entity == null || FileVisibility.from(entity.getVisibility()) != FileVisibility.PUBLIC) {
            return null;
        }
        return publicUrl(entity);
    }

    private FileMetadataView uploadInternal(
            MultipartFile file,
            FileVisibility visibility,
            boolean requirePublicWritePermission,
            boolean imageOnly,
            String auditType
    ) {
        UserAccount user = currentUserService.requireCurrentUser();
        ValidatedUpload validatedUpload = validateUpload(file, imageOnly);
        FileVisibility resolvedVisibility = visibility == null ? FileVisibility.OWNER : visibility;
        assertVisibilityWritable(user, resolvedVisibility, requirePublicWritePermission);

        String tenantId = currentTenantId(user);
        String fileKey = newFileKey();
        String objectKey = buildObjectKey(tenantId, fileKey, file.getOriginalFilename());
        ObjectStorageService.StoredObject storedObject;
        try (InputStream inputStream = new ByteArrayInputStream(validatedUpload.bytes())) {
            storedObject = objectStorageService.put(
                    objectKey,
                    inputStream,
                    validatedUpload.bytes().length,
                    validatedUpload.contentType(),
                    file.getOriginalFilename()
            );
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_ERROR", "文件上传失败");
        }

        SysStorageFileEntity entity = new SysStorageFileEntity();
        entity.setTenantId(tenantId);
        entity.setFileKey(fileKey);
        entity.setOriginalName(safeOriginalName(file.getOriginalFilename()));
        entity.setContentType(validatedUpload.contentType());
        entity.setFileSize((long) validatedUpload.bytes().length);
        entity.setStorageType(storedObject.storageType());
        entity.setBucketName(storedObject.bucketName());
        entity.setObjectKey(storedObject.objectKey());
        entity.setEtag(storedObject.etag());
        entity.setVisibility(resolvedVisibility.name());
        entity.setOwnerUserId(user.id());
        storageFileMapper.insert(entity);

        auditService.record(auditType, user.username(), tenantId, Map.of(
                "fileKey", fileKey,
                "visibility", resolvedVisibility.name(),
                "contentType", validatedUpload.contentType(),
                "size", validatedUpload.bytes().length,
                "storageType", storedObject.storageType()
        ));
        return toView(entity);
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

    private ValidatedUpload validateUpload(MultipartFile file, boolean imageOnly) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "文件不能为空");
        }
        long maxSize = properties.resolvedMaxSizeBytes();
        if (file.getSize() > maxSize) {
            throw new BusinessException("VALIDATION_ERROR", "文件大小超出限制");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_ERROR", "文件读取失败");
        }
        String detectedType = detectContentType(bytes, file.getOriginalFilename());
        List<String> allowedTypes = properties.resolvedAllowedTypes();
        if (!allowedTypes.contains(detectedType)) {
            throw new BusinessException("VALIDATION_ERROR", "不支持的文件类型");
        }
        if (imageOnly && !detectedType.startsWith("image/")) {
            throw new BusinessException("VALIDATION_ERROR", "头像仅支持图片文件");
        }
        String declaredType = normalizeContentType(file.getContentType());
        if (StringUtils.hasText(declaredType) && !declaredType.equals(detectedType)) {
            throw new BusinessException("VALIDATION_ERROR", "文件内容与声明类型不一致");
        }
        return new ValidatedUpload(bytes, detectedType);
    }

    private String detectContentType(byte[] bytes, String originalFilename) {
        if (isPng(bytes)) {
            return "image/png";
        }
        if (isJpeg(bytes)) {
            return "image/jpeg";
        }
        if (isPdf(bytes)) {
            return "application/pdf";
        }
        String guessedType = URLConnection.guessContentTypeFromName(originalFilename);
        String normalized = normalizeContentType(guessedType);
        if (StringUtils.hasText(normalized) && !isStrictType(normalized)) {
            return normalized;
        }
        throw new BusinessException("VALIDATION_ERROR", "无法识别文件真实类型");
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff;
    }

    private boolean isPdf(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == 0x25
                && bytes[1] == 0x50
                && bytes[2] == 0x44
                && bytes[3] == 0x46
                && bytes[4] == 0x2d;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private boolean isStrictType(String contentType) {
        return "image/png".equals(contentType)
                || "image/jpeg".equals(contentType)
                || "application/pdf".equals(contentType);
    }

    private void assertVisibilityWritable(UserAccount user, FileVisibility visibility, boolean requirePublicWritePermission) {
        if (visibility == FileVisibility.PRIVATE && requirePublicWritePermission) {
            throw new BusinessException("VALIDATION_ERROR", "通用文件上传不支持私有可见性，请通过具体业务授权链路创建私有文件");
        }
        if (visibility == FileVisibility.PUBLIC && requirePublicWritePermission && !hasFileWrite(user)) {
            throw new BusinessException("ACCESS_DENIED", "无权创建该可见性文件");
        }
    }

    private void applyReadableScope(LambdaQueryWrapper<SysStorageFileEntity> wrapper, UserAccount user) {
        String activeTenantId = currentTenantId(user);
        if (platformAdminSupport.isPlatformSuperAdmin(user)) {
            return;
        }
        wrapper.eq(SysStorageFileEntity::getTenantId, activeTenantId);
        if (!hasFileRead(user)) {
            wrapper.and(scope -> scope
                    .eq(SysStorageFileEntity::getVisibility, FileVisibility.PUBLIC.name())
                    .or()
                    .eq(SysStorageFileEntity::getOwnerUserId, user.id()));
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
            case PRIVATE -> {
                boolean owner = entity.getOwnerUserId() != null && entity.getOwnerUserId().equals(user.id());
                boolean sameTenantAdmin = entity.getTenantId().equals(activeTenantId) && hasFileRead(user);
                boolean platformAdmin = platformAdminSupport.isPlatformSuperAdmin(user);
                if (!owner && !sameTenantAdmin && !platformAdmin) {
                    throw new BusinessException("ACCESS_DENIED", "无权访问私有文件");
                }
            }
        }
    }

    private void assertDeletable(SysStorageFileEntity entity, UserAccount user) {
        boolean owner = entity.getOwnerUserId() != null && entity.getOwnerUserId().equals(user.id());
        boolean sameTenantAdmin = entity.getTenantId().equals(currentTenantId(user)) && hasFileWrite(user);
        boolean platformAdmin = platformAdminSupport.isPlatformSuperAdmin(user);
        if (!owner && !sameTenantAdmin && !platformAdmin) {
            throw new BusinessException("ACCESS_DENIED", "无权删除文件");
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
                TimeSupport.toEpochMilli(entity.getCreatedAt()),
                FileVisibility.from(entity.getVisibility()) == FileVisibility.PUBLIC ? publicUrl(entity) : null
        );
    }

    private String publicUrl(SysStorageFileEntity entity) {
        String path = "/api/files/public/" + entity.getFileKey();
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(path)
                    .toUriString();
        } catch (IllegalStateException ignored) {
            return path;
        }
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

    private record ValidatedUpload(byte[] bytes, String contentType) {
    }
}