package com.aiot.domain.model;

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
    RESCUE
}