/**
 * 车载安全监测系统 — 认证 API（ArkTS 兼容）
 *
 * 基于 docs/ood_interface.md §1.7 Auth 认证服务
 */

import { apiClient, type ApiResponse } from './ApiClient'

export class AuthApi {
  /** POST /api/v1/auth/login */
  async login(request: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/auth/login', request)
  }

  /** POST /api/v1/auth/refresh */
  async refresh(request: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/auth/refresh', request)
  }

  /** POST /api/v1/auth/secondary-verify */
  async secondaryVerify(request: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/auth/secondary-verify', request)
  }
}

export const authApi = new AuthApi()
