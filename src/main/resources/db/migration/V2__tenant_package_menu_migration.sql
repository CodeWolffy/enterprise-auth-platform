SET time_zone = '+00:00';

DROP PROCEDURE IF EXISTS migrate_tenant_package_menu_v2;

DELIMITER //
CREATE PROCEDURE migrate_tenant_package_menu_v2()
BEGIN
    DECLARE object_count INT DEFAULT 0;

    SELECT COUNT(*) INTO object_count
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_tenant_package'
      AND COLUMN_NAME = 'status';
    IF object_count = 0 THEN
        ALTER TABLE sys_tenant_package
            ADD COLUMN status char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用' AFTER package_desc;
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_tenant_package'
      AND COLUMN_NAME = 'enabled';
    IF object_count > 0 THEN
        UPDATE sys_tenant_package
        SET status = CASE WHEN enabled = 0 THEN '1' ELSE '0' END;
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_tenant_package'
      AND INDEX_NAME = 'idx_sys_tenant_package_tenant_enabled_order';
    IF object_count > 0 THEN
        ALTER TABLE sys_tenant_package DROP INDEX idx_sys_tenant_package_tenant_enabled_order;
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_tenant_package'
      AND INDEX_NAME = 'idx_sys_tenant_package_tenant_status_order';
    IF object_count = 0 THEN
        ALTER TABLE sys_tenant_package
            ADD INDEX idx_sys_tenant_package_tenant_status_order(tenant_id, deleted, status, order_no);
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_tenant_package'
      AND COLUMN_NAME = 'enabled';
    IF object_count > 0 THEN
        ALTER TABLE sys_tenant_package DROP COLUMN enabled;
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_tenant_package'
      AND COLUMN_NAME = 'user_quota';
    IF object_count > 0 THEN
        ALTER TABLE sys_tenant_package DROP COLUMN user_quota;
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_tenant_package'
      AND COLUMN_NAME = 'storage_quota_gb';
    IF object_count > 0 THEN
        ALTER TABLE sys_tenant_package DROP COLUMN storage_quota_gb;
    END IF;
END//
DELIMITER ;

CALL migrate_tenant_package_menu_v2();
DROP PROCEDURE migrate_tenant_package_menu_v2;

UPDATE sys_tenant_package
SET app_key = CASE package_code
    WHEN 'DEFAULT' THEN 'base,system,platform,workflow,dev'
    WHEN 'BASIC' THEN 'base,system'
    ELSE app_key
END
WHERE tenant_id = 'platform'
  AND (app_key IS NULL OR TRIM(app_key) = '')
  AND package_code IN ('DEFAULT', 'BASIC');

UPDATE sys_menu
SET permission = REPLACE(permission, 'upms:tenantcatalog:', 'upms:tenantpackage:')
WHERE permission LIKE 'upms:tenantcatalog:%';

INSERT INTO sys_tenant_menu (tenant_id, menu_id, create_by, create_time)
SELECT 'platform', m.id, 'system', UTC_TIMESTAMP()
FROM sys_menu m
WHERE m.del_flag = '0'
  AND m.id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_tenant_menu tm
      WHERE tm.tenant_id = 'platform'
        AND tm.menu_id = m.id
  );

INSERT INTO sys_tenant_menu (tenant_id, menu_id, create_by, create_time)
SELECT t.tenant_id, m.id, 'system', UTC_TIMESTAMP()
FROM sys_tenant t
JOIN sys_tenant_package p
  ON p.tenant_id = 'platform'
 AND p.package_code = t.package_code
 AND p.deleted = 0
JOIN sys_menu m
  ON m.del_flag = '0'
 AND m.id IS NOT NULL
 AND FIND_IN_SET(
        m.application_key,
        REPLACE(REPLACE(REPLACE(TRIM(COALESCE(p.app_key, '')), ';', ','), ' ', ','), CHAR(9), ',')
     ) > 0
WHERE t.deleted = 0
  AND t.tenant_id <> 'platform'
  AND TRIM(COALESCE(p.app_key, '')) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM sys_tenant_menu tm
      WHERE tm.tenant_id = t.tenant_id
        AND tm.menu_id = m.id
  );

DROP TABLE IF EXISTS sys_tenant_capability_resource_scope;
DROP TABLE IF EXISTS sys_tenant_capability_override;
DROP TABLE IF EXISTS sys_tenant_package_capability;
DROP TABLE IF EXISTS sys_tenant_capability;
