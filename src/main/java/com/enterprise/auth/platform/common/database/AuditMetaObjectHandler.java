package com.enterprise.auth.platform.common.database;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.CurrentOperatorSupplier;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final ObjectProvider<CurrentOperatorSupplier> operatorSupplier;

    public AuditMetaObjectHandler(ObjectProvider<CurrentOperatorSupplier> operatorSupplier) {
        this.operatorSupplier = operatorSupplier;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        String operator = resolveOperator();
        Instant now = TimeSupport.now();

        strictInsertFill(metaObject, "createdBy", String.class, operator);
        strictInsertFill(metaObject, "updatedBy", String.class, operator);
        strictInsertFill(metaObject, "createdAt", Instant.class, now);
        strictInsertFill(metaObject, "updatedAt", Instant.class, now);

        Object deleted = getFieldValByName("deleted", metaObject);
        if (deleted == null) {
            setFieldValByName("deleted", 0, metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedBy", String.class, resolveOperator());
        strictUpdateFill(metaObject, "updatedAt", Instant.class, TimeSupport.now());
    }

    private String resolveOperator() {
        CurrentOperatorSupplier supplier = operatorSupplier.getIfAvailable();
        if (supplier == null) {
            return "system";
        }
        try {
            String operator = supplier.currentOperator();
            return StringUtilsHasText(operator) ? operator : "system";
        } catch (RuntimeException ex) {
            return "system";
        }
    }

    private static boolean StringUtilsHasText(String value) {
        return value != null && !value.isBlank();
    }
}