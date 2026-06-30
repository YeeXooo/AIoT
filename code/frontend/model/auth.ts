/**
 * 车载安全监测系统 — 认证服务 DTO（Auth / §1.7）
 *
 * 基于 docs/ood_interface.md §1.7 Auth 认证服务
 *
 * fromJson 构造器：ArkTS 严格模式禁止 as T（T 为接口），因此为每个 Response DTO
 * 提供静态 fromJson(raw: Record<string, unknown>) 构造器，用基础类型断言逐字段提取，
 * 使 API 层可返回具体 DTO 类型而非 ApiResponse<Record<string, unknown>>（问题 4 修复）。
 */

import { getStr, getNum } from '../common/JsonParser'
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

/** ArkTS-safe 构造器：从原始 JSON 构建 LoginResponse（不依赖同名合并声明） */
export function loginResponseFromJson(raw: Record<string, unknown>): LoginResponse {
  return {
    accessToken: getStr(raw, 'accessToken'),
    refreshToken: getStr(raw, 'refreshToken'),
    tokenType: getStr(raw, 'tokenType'),
    expiresIn: getNum(raw, 'expiresIn'),
    accountId: getStr(raw, 'accountId'),
    role: getStr(raw, 'role') as AccountRole,
  }
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

/** ArkTS-safe 构造器：从原始 JSON 构建 RefreshTokenResponse */
export function refreshTokenResponseFromJson(raw: Record<string, unknown>): RefreshTokenResponse {
  return {
    accessToken: getStr(raw, 'accessToken'),
    refreshToken: getStr(raw, 'refreshToken'),
    tokenType: getStr(raw, 'tokenType'),
    expiresIn: getNum(raw, 'expiresIn'),
  }
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

/** ArkTS-safe 构造器：从原始 JSON 构建 SecondaryVerifyResponse */
export function secondaryVerifyResponseFromJson(raw: Record<string, unknown>): SecondaryVerifyResponse {
  return {
    secondaryAuthToken: getStr(raw, 'secondaryAuthToken'),
    expiresAt: getStr(raw, 'expiresAt'),
  }
}
