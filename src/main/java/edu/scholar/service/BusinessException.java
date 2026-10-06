package edu.scholar.service;

/** 业务规则校验失败 -> 400；权限/自审拦截 -> 403（用 ForbiddenException） */
public class BusinessException extends RuntimeException {
    public final int status;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }
}
