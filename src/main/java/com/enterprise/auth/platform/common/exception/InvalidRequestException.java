package com.enterprise.auth.platform.common.exception;

/**
 * 表示已经明确识别为客户端请求输入无效的异常。
 *
 * <p>不要用它包装内部配置、持久化数据或程序状态错误，这些错误应由兜底异常处理器按 500 处理。</p>
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
