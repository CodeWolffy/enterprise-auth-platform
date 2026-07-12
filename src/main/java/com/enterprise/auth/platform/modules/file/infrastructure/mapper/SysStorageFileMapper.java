package com.enterprise.auth.platform.modules.file.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysStorageFileMapper extends BaseMapper<SysStorageFileEntity> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT COALESCE(SUM(file_size), 0)
            FROM sys_storage_file
            WHERE deleted = 0
              AND lifecycle_status = 'READY'
            <if test="!platformScope">
              AND tenant_id = #{tenantId}
            </if>
            <if test="ownerUserIds != null">
              <choose>
                <when test="ownerUserIds.size() == 0">
                  AND 1 = 0
                </when>
                <otherwise>
                  AND owner_user_id IN
                  <foreach collection="ownerUserIds" item="ownerUserId" open="(" separator="," close=")">
                    #{ownerUserId}
                  </foreach>
                </otherwise>
              </choose>
            </if>
            </script>
            """)
    Long sumFileSize(
            @Param("tenantId") String tenantId,
            @Param("platformScope") boolean platformScope,
            @Param("ownerUserIds") Set<Long> ownerUserIds
    );

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM sys_storage_file
            WHERE deleted = 0
              AND lifecycle_status = 'READY'
            <if test="!platformScope">
              AND tenant_id = #{tenantId}
            </if>
            <if test="keyword != null and keyword != ''">
              AND original_name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="contentType != null and contentType != ''">
              AND content_type = #{contentType}
            </if>
            <if test="storageType != null and storageType != ''">
              AND storage_type = #{storageType}
            </if>
            <if test="visibility != null and visibility != ''">
              AND visibility = #{visibility}
            </if>
            <if test="restrictReadable">
              AND (visibility = 'PUBLIC' OR owner_user_id = #{ownerUserId})
            </if>
            </script>
            """)
    long countReadableFiles(
            @Param("tenantId") String tenantId,
            @Param("platformScope") boolean platformScope,
            @Param("keyword") String keyword,
            @Param("contentType") String contentType,
            @Param("storageType") String storageType,
            @Param("visibility") String visibility,
            @Param("restrictReadable") boolean restrictReadable,
            @Param("ownerUserId") Long ownerUserId
    );

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT *
            FROM sys_storage_file
            WHERE deleted = 0
              AND lifecycle_status = 'READY'
            <if test="!platformScope">
              AND tenant_id = #{tenantId}
            </if>
            <if test="keyword != null and keyword != ''">
              AND original_name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="contentType != null and contentType != ''">
              AND content_type = #{contentType}
            </if>
            <if test="storageType != null and storageType != ''">
              AND storage_type = #{storageType}
            </if>
            <if test="visibility != null and visibility != ''">
              AND visibility = #{visibility}
            </if>
            <if test="restrictReadable">
              AND (visibility = 'PUBLIC' OR owner_user_id = #{ownerUserId})
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<SysStorageFileEntity> selectReadableFiles(
            @Param("tenantId") String tenantId,
            @Param("platformScope") boolean platformScope,
            @Param("keyword") String keyword,
            @Param("contentType") String contentType,
            @Param("storageType") String storageType,
            @Param("visibility") String visibility,
            @Param("restrictReadable") boolean restrictReadable,
            @Param("ownerUserId") Long ownerUserId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM sys_storage_file
            WHERE file_key = #{fileKey}
              AND deleted = 0
            LIMIT 1
            """)
    SysStorageFileEntity selectByFileKeyIgnoreTenant(@Param("fileKey") String fileKey);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_storage_file
            SET lifecycle_status = #{status},
                etag = #{etag},
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
              AND deleted = 0
            """)
    int updateLifecycle(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("etag") String etag
    );

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM sys_storage_file
            WHERE deleted = 0
              AND lifecycle_status = #{status}
            ORDER BY updated_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<SysStorageFileEntity> selectByLifecycle(
            @Param("status") String status,
            @Param("limit") int limit
    );

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_storage_file
            SET deleted = 1,
                lifecycle_status = 'READY',
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
              AND deleted = 0
            """)
    int softDeleteByIdIgnoreTenant(@Param("id") Long id);
}
