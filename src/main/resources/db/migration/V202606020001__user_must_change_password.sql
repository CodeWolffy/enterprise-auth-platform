ALTER TABLE sys_user
    ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须修改密码' AFTER session_version;