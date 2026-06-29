/**
 * 车载安全监测系统 — 驾驶员风险监测 API（S1，ArkTS 兼容）
 */

import { apiClient, type ApiResponse } from './ApiClient'

export class DriverApi {
  /** GET /api/v1/drivers/{driverId}/risk-status */
  async getRiskStatus(driverId: string): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.get(`/drivers/${driverId}/risk-status`)
  }

  /** GET /api/v1/drivers/{driverId}/alerts */
  async queryAlertHistory(driverId: string, params?: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.get(`/drivers/${driverId}/alerts`, { params })
  }
}

export const driverApi = new DriverApi()
