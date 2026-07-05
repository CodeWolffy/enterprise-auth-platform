-- V29: Remove the code generation table allowlist.
-- Imported codegen_table rows are now the generation boundary.
DROP TABLE IF EXISTS `sys_codegen_allowlist`;
