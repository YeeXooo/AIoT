package com.aiot.domain.model.exception;

/**
 * 业务异常基类（替代通用RuntimeException）
 * 所有业务校验失败均抛出此异常/其子类
 */
public class BusinessException extends RuntimeException {
    // 业务错误码（可按模块/场景定义，如MODEL_001=账户角色非法，MODEL_002=经纬度超出范围）
    private String errorCode;
    // 业务场景说明（可选，便于排查）
    private String businessScene;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.businessScene = "MODEL_VALIDATE"; // 统一标记为模型校验场景
    }

    public BusinessException(String errorCode, String message, String businessScene) {
        super(message);
        this.errorCode = errorCode;
        this.businessScene = businessScene;
    }

    // getter/setter
    public String getErrorCode() { return errorCode; }
    public String getBusinessScene() { return businessScene; }
}