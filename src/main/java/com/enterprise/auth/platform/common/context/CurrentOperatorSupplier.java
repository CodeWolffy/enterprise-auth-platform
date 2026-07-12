package com.enterprise.auth.platform.common.context;

/**
 * 当前操作人解析端口：common 只依赖此接口，由 auth 模块提供实现。
 */
@FunctionalInterface
public interface CurrentOperatorSupplier {

    String currentOperator();

    static CurrentOperatorSupplier system() {
        return () -> "system";
    }
}