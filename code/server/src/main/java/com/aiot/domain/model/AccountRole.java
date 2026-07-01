package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;

/**
 * 账户角色（VO-14）
 * 标识系统账户的角色类别，驱动权限判定与通知路由分支
 */
public enum AccountRole {
    /** 家属用户 */
    FAMILY,
    /** 车队管理员 */
    MANAGER,
    /** 救援机构 */
    RESCUE;

    /**
     * 字符串转账户角色枚举，内置业务校验
     * @param roleCode 角色字符串
     * @return 对应枚举值
     * @throws BusinessException 空值/非法值时抛出
     */
    public static AccountRole of(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException(
                    "MODEL_007",
                    "账户角色不能为空",
                    "ACCOUNT_ROLE_VALIDATE"
            );
        }
        try {
            return AccountRole.valueOf(roleCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "MODEL_008",
                    String.format("账户角色非法，当前值：%s，合法值：FAMILY、MANAGER、RESCUE", roleCode),
                    "ACCOUNT_ROLE_VALIDATE"
            );
        }
    }

    public boolean isFamily() {
        return this == FAMILY;
    }

    public boolean isManager() {
        return this == MANAGER;
    }

    public boolean isRescue() {
        return this == RESCUE;
    }

}