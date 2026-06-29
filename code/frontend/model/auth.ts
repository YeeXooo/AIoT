/**
 * 车载安全监测系统 — 认证服务 DTO（Auth / §1.7）
 *
 * 基于 docs/ood_interface.md §1.7 Auth 认证服务
 */

import type { AccountRole, AuthMethod, SecondaryVerifyMethod } from './types'

// ===================================================================
// 登录
// ===================================================================

export interface LoginRequest {
  /** 认证方式：PASSWORD（用户名+密码）| SMS_CODE（手机号+短信验证码） */
  authMethod: AuthMethod
  /** PASSWORD 模式为用户名或邮箱，SMS_CODE 模式为手机号 */
  credential: string
  /** PASSWORD 模式为密码，SMS_CODE 模式为短信验证码 */
  secret: string
}

export interface LoginResponse {
  /** JWT access token */
  accessToken: string
  /** Refresh token（轮换策略） */
  refreshToken: string
  /** Token 类型，固定 Bearer */
  tokenType: string
  /** access token 有效期（秒），默认 3600 */
  expiresIn: number
  /** 账户 ID */
  accountId: string
  /** 账户角色 */
  role: AccountRole
}

// ===================================================================
// Token 刷新
// ===================================================================

export interface RefreshTokenRequest {
  /** 登录时获得的 refresh token */
  refreshToken: string
}

export interface RefreshTokenResponse {
  /** 新签发的 JWT access token */
  accessToken: string
  /** 新签发的 refresh token（旧 token 同时失效） */
  refreshToken: string
  /** Token 类型，固定 Bearer */
  tokenType: string
  /** 新 access token 的有效期（秒），默认 3600 */
  expiresIn: number
}

// ===================================================================
// 二次身份验证
// ===================================================================

export interface SecondaryVerifyRequest {
  /** 账户 ID */
  accountId: string
  /** 验证方式：OTP（动态短信验证码）| BIOMETRIC（生物特征凭证） */
  method: SecondaryVerifyMethod
  /** OTP 验证码（method=OTP 时必填）；method=BIOMETRIC 时填生物凭证 */
  otp?: string
}

export interface SecondaryVerifyResponse {
  /** 二次身份验证凭证，有效期 5 分钟 */
  secondaryAuthToken: string
  /** 凭证过期时间（ISO 8601） */
  expiresAt: string
}
