-- =====================================================
-- P2 字典二级模型语义收敛：数据清洗与唯一约束
-- =====================================================

ALTER TABLE `sys_dict`
  DROP INDEX `uk_sys_dict_tenant_type_code`;

UPDATE `sys_dict`
SET
  `dict_type` = COALESCE(NULLIF(TRIM(`dict_type`), ''), CONCAT('dict_', `id`)),
  `dict_code` = COALESCE(NULLIF(TRIM(`dict_code`), ''), COALESCE(NULLIF(TRIM(`dict_type`), ''), CONCAT('dict_', `id`))),
  `dict_value` = COALESCE(NULLIF(TRIM(`dict_value`), ''), COALESCE(NULLIF(TRIM(`dict_code`), ''), COALESCE(NULLIF(TRIM(`dict_type`), ''), CONCAT('dict_', `id`))));

UPDATE `sys_dict_value` v
LEFT JOIN `sys_dict` d ON d.`tenant_id` = v.`tenant_id` AND d.`id` = v.`dict_id`
SET
  v.`dict_type` = COALESCE(d.`dict_type`, NULLIF(TRIM(v.`dict_type`), ''), CONCAT('dict_', v.`dict_id`)),
  v.`dict_label` = COALESCE(NULLIF(TRIM(v.`dict_label`), ''), NULLIF(TRIM(v.`dict_value`), ''), CONCAT('value_', v.`id`)),
  v.`dict_value` = COALESCE(NULLIF(TRIM(v.`dict_value`), ''), CONCAT('value_', v.`id`));

UPDATE `sys_dict`
SET `description` = COALESCE(NULLIF(TRIM(`description`), ''), `dict_value`, `dict_type`)
WHERE `deleted` = 0;

UPDATE `sys_dict_value` v
JOIN `sys_dict` d ON d.`tenant_id` = v.`tenant_id` AND d.`id` = v.`dict_id`
JOIN (
  SELECT `tenant_id`, `dict_type`, MIN(`id`) AS `keep_id`
  FROM `sys_dict`
  WHERE `deleted` = 0
  GROUP BY `tenant_id`, `dict_type`
) keepers ON keepers.`tenant_id` = d.`tenant_id` AND keepers.`dict_type` = d.`dict_type`
SET
  v.`dict_id` = keepers.`keep_id`,
  v.`dict_type` = keepers.`dict_type`
WHERE v.`deleted` = 0;

UPDATE `sys_dict_value` v
LEFT JOIN `sys_dict` d ON d.`tenant_id` = v.`tenant_id` AND d.`id` = v.`dict_id` AND d.`deleted` = 0
SET
  v.`dict_value` = CONCAT(LEFT(v.`dict_value`, GREATEST(0, 255 - CHAR_LENGTH(CONCAT('#deleted#', v.`id`)))), '#deleted#', v.`id`),
  v.`deleted` = 1
WHERE v.`deleted` = 0
  AND d.`id` IS NULL;

UPDATE `sys_dict_value` v
JOIN (
  SELECT `tenant_id`, `dict_id`, `dict_value`, MIN(`id`) AS `keep_id`
  FROM `sys_dict_value`
  WHERE `deleted` = 0
  GROUP BY `tenant_id`, `dict_id`, `dict_value`
  HAVING COUNT(*) > 1
) duplicates ON duplicates.`tenant_id` = v.`tenant_id`
  AND duplicates.`dict_id` = v.`dict_id`
  AND duplicates.`dict_value` = v.`dict_value`
SET
  v.`dict_value` = CONCAT(LEFT(v.`dict_value`, GREATEST(0, 255 - CHAR_LENGTH(CONCAT('#deleted#', v.`id`)))), '#deleted#', v.`id`),
  v.`deleted` = 1
WHERE v.`id` <> duplicates.`keep_id`;

UPDATE `sys_dict` d
JOIN (
  SELECT `tenant_id`, `dict_type`, MIN(`id`) AS `keep_id`
  FROM `sys_dict`
  WHERE `deleted` = 0
  GROUP BY `tenant_id`, `dict_type`
  HAVING COUNT(*) > 1
) duplicates ON duplicates.`tenant_id` = d.`tenant_id` AND duplicates.`dict_type` = d.`dict_type`
SET
  d.`dict_type` = CONCAT(LEFT(d.`dict_type`, GREATEST(0, 64 - CHAR_LENGTH(CONCAT('#deleted#', d.`id`)))), '#deleted#', d.`id`),
  d.`dict_code` = CONCAT(LEFT(d.`dict_code`, GREATEST(0, 64 - CHAR_LENGTH(CONCAT('#deleted#', d.`id`)))), '#deleted#', d.`id`),
  d.`dict_value` = CONCAT(LEFT(d.`dict_value`, GREATEST(0, 255 - CHAR_LENGTH(CONCAT('#deleted#', d.`id`)))), '#deleted#', d.`id`),
  d.`deleted` = 1
WHERE d.`id` <> duplicates.`keep_id`;

UPDATE `sys_dict`
SET
  `dict_type` = CASE
    WHEN `dict_type` LIKE CONCAT('%#deleted#', `id`) THEN `dict_type`
    ELSE CONCAT(LEFT(`dict_type`, GREATEST(0, 64 - CHAR_LENGTH(CONCAT('#deleted#', `id`)))), '#deleted#', `id`)
  END,
  `dict_code` = CASE
    WHEN `dict_code` LIKE CONCAT('%#deleted#', `id`) THEN `dict_code`
    ELSE CONCAT(LEFT(`dict_code`, GREATEST(0, 64 - CHAR_LENGTH(CONCAT('#deleted#', `id`)))), '#deleted#', `id`)
  END,
  `dict_value` = CASE
    WHEN `dict_value` LIKE CONCAT('%#deleted#', `id`) THEN `dict_value`
    ELSE CONCAT(LEFT(`dict_value`, GREATEST(0, 255 - CHAR_LENGTH(CONCAT('#deleted#', `id`)))), '#deleted#', `id`)
  END
WHERE `deleted` <> 0;

UPDATE `sys_dict_value`
SET `dict_value` = CASE
  WHEN `dict_value` LIKE CONCAT('%#deleted#', `id`) THEN `dict_value`
  ELSE CONCAT(LEFT(`dict_value`, GREATEST(0, 255 - CHAR_LENGTH(CONCAT('#deleted#', `id`)))), '#deleted#', `id`)
END
WHERE `deleted` <> 0;

UPDATE `sys_dict`
SET
  `description` = COALESCE(NULLIF(TRIM(`description`), ''), `dict_value`, `dict_type`),
  `dict_code` = `dict_type`,
  `dict_value` = COALESCE(NULLIF(TRIM(`description`), ''), `dict_value`, `dict_type`),
  `enabled` = COALESCE(`enabled`, 1)
WHERE `deleted` = 0;

CREATE UNIQUE INDEX `uk_sys_dict_tenant_type`
  ON `sys_dict` (`tenant_id`, `dict_type`);

CREATE UNIQUE INDEX `uk_sys_dict_value_tenant_dict_value`
  ON `sys_dict_value` (`tenant_id`, `dict_id`, `dict_value`);