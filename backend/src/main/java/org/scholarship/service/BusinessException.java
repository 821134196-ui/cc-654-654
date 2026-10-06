package org.scholarship.service;

/** 业务规则违例，携带 HTTP 状态码（403 自审拦截、409 状态冲突、400 参数错误等）。 */
public class BusinessException extends RuntimeException {
    private final int status;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
