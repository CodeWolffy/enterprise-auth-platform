-- Before applying this migration, clean up duplicate active usernames.
SELECT username, COUNT(1) AS duplicate_count
FROM sys_user
WHERE deleted = 0
GROUP BY username
HAVING COUNT(1) > 1;

ALTER TABLE sys_user
    DROP INDEX uk_sys_user_tenant_username,
    ADD UNIQUE INDEX uk_sys_user_username (username);
