SET NAMES utf8mb4;
SET time_zone = '+00:00';

DROP PROCEDURE IF EXISTS ea_drop_index_if_exists;
DROP PROCEDURE IF EXISTS ea_create_index_if_missing;
DROP PROCEDURE IF EXISTS ea_merge_column;

DELIMITER //

CREATE PROCEDURE ea_drop_index_if_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` DROP INDEX `', p_index_name, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE ea_create_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_columns TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD INDEX `', p_index_name, '` (', p_columns, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE ea_merge_column(
    IN p_table_name VARCHAR(64),
    IN p_old_column VARCHAR(64),
    IN p_new_column VARCHAR(64),
    IN p_new_definition TEXT,
    IN p_sync_expression TEXT
)
BEGIN
    DECLARE v_old_exists INT DEFAULT 0;
    DECLARE v_new_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO v_old_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND column_name = p_old_column;

    SELECT COUNT(*) INTO v_new_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND column_name = p_new_column;

    IF v_old_exists > 0 AND v_new_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` CHANGE COLUMN `', p_old_column, '` `', p_new_column, '` ', p_new_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSEIF v_old_exists > 0 AND v_new_exists > 0 THEN
        IF p_sync_expression IS NOT NULL AND LENGTH(TRIM(p_sync_expression)) > 0 THEN
            SET @sql = CONCAT('UPDATE `', p_table_name, '` SET `', p_new_column, '` = ', p_sync_expression);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` DROP COLUMN `', p_old_column, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CALL ea_drop_index_if_exists('sys_menu', 'idx_sys_menu_del_flag');

CALL ea_merge_column('sys_menu', 'del_flag', 'deleted', 'tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除标记''', 'CASE WHEN `del_flag` = ''1'' THEN 1 ELSE `deleted` END');
CALL ea_merge_column('sys_menu', 'create_by', 'created_by', 'varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''创建人''', 'COALESCE(`created_by`, `create_by`)');
CALL ea_merge_column('sys_menu', 'update_by', 'updated_by', 'varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''更新人''', 'COALESCE(`updated_by`, `update_by`)');
CALL ea_merge_column('sys_menu', 'create_time', 'created_at', 'datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''', 'COALESCE(`create_time`, `created_at`)');
CALL ea_merge_column('sys_menu', 'update_time', 'updated_at', 'datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'COALESCE(`update_time`, `updated_at`)');
CALL ea_create_index_if_missing('sys_menu', 'idx_sys_menu_deleted', '`deleted` ASC');

CALL ea_merge_column('sys_log', 'del_flag', 'deleted', 'tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除标记''', NULL);
CALL ea_create_index_if_missing('sys_log', 'idx_sys_log_deleted', '`deleted` ASC');

CALL ea_merge_column('sys_login_log', 'del_flag', 'deleted', 'tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除标记''', NULL);
CALL ea_create_index_if_missing('sys_login_log', 'idx_sys_login_log_deleted', '`deleted` ASC');

CALL ea_merge_column('sys_role_menu', 'create_time', 'created_at', 'datetime NULL DEFAULT NULL COMMENT ''创建时间''', NULL);

CALL ea_merge_column('sys_tenant_menu', 'create_by', 'created_by', 'varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''创建人''', NULL);
CALL ea_merge_column('sys_tenant_menu', 'create_time', 'created_at', 'datetime NULL DEFAULT NULL COMMENT ''创建时间''', NULL);

DROP PROCEDURE IF EXISTS ea_merge_column;
DROP PROCEDURE IF EXISTS ea_create_index_if_missing;
DROP PROCEDURE IF EXISTS ea_drop_index_if_exists;