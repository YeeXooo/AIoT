/**
 * 车载安全监测系统 — 驾驶员风险监测 API（S1，ArkTS 兼容）
 *
 * 问题 4 修复：API 层返回具体 DTO 类型而非 ApiResponse<Record<string, Object>>。
 */

import { apiClient, type ApiResponse } from './ApiClient'
import {
  getDriverRiskStatusResponseFromJson,
  queryAlertHistoryResponseFromJson,
} from '../model/driver'
import type {
  GetDriverRiskStatusResponse,
  QueryAlertHistoryResponse,
} from '../model/driver'

export class DriverApi {
  /** GET /api/v1/drivers/{driverId}/risk-status */
  async getRiskStatus(driverId: string): Promise<ApiResponse<GetDriverRiskStatusResponse>> {
    const resp = await apiClient.get(`/drivers/${driverId}/risk-status`)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: getDriverRiskStatusResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** POST /api/v1/guardianship/bind */
  async bindDriver(accountId: string, driverId: string): Promise<ApiResponse<Record<string, Object>>> {
    const body: Record<string, Object> = {
      'familyAccountId': accountId,
      'driverId': driverId,
    }
    return apiClient.post('/guardianship/bind', body)
  }

  /** GET /api/v1/drivers/{driverId}/alerts */
  async queryAlertHistory(driverId: string, params?: Record<string, Object>): Promise<ApiResponse<QueryAlertHistoryResponse>> {
    const resp = await apiClient.get(`/drivers/${driverId}/alerts`, { params })
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: queryAlertHistoryResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }
}

export const driverApi = new DriverApi()
