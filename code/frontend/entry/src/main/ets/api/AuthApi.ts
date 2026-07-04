/**
 * 车载安全监测系统 — 认证 API（ArkTS 兼容）
 *
 * 基于 docs/ood_interface.md §1.7 Auth 认证服务
 *
 * 问题 4 修复：API 层返回具体 DTO 类型而非 ApiResponse<Record<string, Object>>。
 * 内部仍调用 apiClient.post 拿到 ApiResponse<Record>，success 时用 model 的
 * fromJson 构造器把原始 Record 转为具体 DTO，再包装为 ApiResponse<具体DTO> 返回。
 */

import { hilog } from '@kit.PerformanceAnalysisKit'
import { apiClient, type ApiResponse } from './ApiClient'
import {
  loginResponseFromJson,
  refreshTokenResponseFromJson,
  secondaryVerifyResponseFromJson,
} from '../model/auth'
import type {
  LoginResponse,
  RefreshTokenResponse,
  SecondaryVerifyResponse,
} from '../model/auth'

const DOMAIN = 0x0501
const TAG = 'AuthApi'

export class AuthApi {
  /** POST /api/v1/auth/login */
  async login(request: Record<string, Object>): Promise<ApiResponse<LoginResponse>> {
    const resp = await apiClient.post('/auth/login', request)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: loginResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    // 防御性日志：success=true 但 data 缺失，说明 ApiClient 把响应当作"非 JSON"
    // 处理（Content-Type 解析失败），帮助快速定位响应头问题
    if (resp.success && resp.data === undefined) {
      hilog.error(DOMAIN, TAG, 'login resp success but data undefined, status=%{public}d (Content-Type 解析失败?)', resp.status)
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** POST /api/v1/auth/refresh */
  async refresh(request: Record<string, Object>): Promise<ApiResponse<RefreshTokenResponse>> {
    const resp = await apiClient.post('/auth/refresh', request)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: refreshTokenResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    if (resp.success && resp.data === undefined) {
      hilog.error(DOMAIN, TAG, 'refresh resp success but data undefined, status=%{public}d (Content-Type 解析失败?)', resp.status)
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** POST /api/v1/auth/secondary-verify */
  async secondaryVerify(request: Record<string, Object>): Promise<ApiResponse<SecondaryVerifyResponse>> {
    const resp = await apiClient.post('/auth/secondary-verify', request)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: secondaryVerifyResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    if (resp.success && resp.data === undefined) {
      hilog.error(DOMAIN, TAG, 'secondaryVerify resp success but data undefined, status=%{public}d (Content-Type 解析失败?)', resp.status)
    }
    return { success: false, error: resp.error, status: resp.status }
  }
}

export const authApi = new AuthApi()
