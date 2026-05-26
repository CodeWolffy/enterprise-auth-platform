package com.enterprise.auth.platform.common.database;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        String operator = SecuritySupport.currentOperator();
        LocalDateTime now = TimeSupport.utcNowDateTime();

        strictInsertFill(metaObject, "createdBy", String.class, operator);
        strictInsertFill(metaObject, "updatedBy", String.class, operator);
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);

        Object deleted = getFieldValByName("deleted", metaObject);
        if (deleted == null) {
            setFieldValByName("deleted", 0, metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedBy", String.class, SecuritySupport.currentOperator());
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, TimeSupport.utcNowDateTime());
    }
}
