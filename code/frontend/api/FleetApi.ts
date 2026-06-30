/**
 * 车载安全监测系统 — 车队管理 API（S4，ArkTS 兼容）
 *
 * 问题 4 修复：API 层返回具体 DTO 类型而非 ApiResponse<Record<string, unknown>>。
 * 仅对有 fromJson 构造器的响应 DTO 接入类型化返回；其余暂保留 Record 返回。
 */

import { apiClient, type ApiResponse } from './ApiClient'
import {
  getFatigueDistributionResponseFromJson,
  getOfflineVehiclesResponseFromJson,
  queryTrajectoryResponseFromJson,
  drillDownHighRiskResponseFromJson,
  generateReportResponseFromJson,
  subscribePerformanceWarningResponseFromJson,
} from '../model/fleet'
import type {
  GetFatigueDistributionResponse,
  GetOfflineVehiclesResponse,
  QueryTrajectoryResponse,
  DrillDownHighRiskResponse,
  GenerateReportResponse,
  SubscribePerformanceWarningResponse,
} from '../model/fleet'

export class FleetApi {
  /** GET /api/v1/fleet/{fleetId}/fatigue-distribution */
  async getFatigueDistribution(fleetId: string, params?: Record<string, unknown>): Promise<ApiResponse<GetFatigueDistributionResponse>> {
    const resp = await apiClient.get(`/fleet/${fleetId}/fatigue-distribution`, { params })
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: getFatigueDistributionResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** GET /api/v1/fleet/{fleetId}/offline-vehicles */
  async getOfflineVehicles(fleetId: string): Promise<ApiResponse<GetOfflineVehiclesResponse>> {
    const resp = await apiClient.get(`/fleet/${fleetId}/offline-vehicles`)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: getOfflineVehiclesResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** GET /api/v1/fleet/{fleetId}/trajectory */
  async queryTrajectory(fleetId: string, params?: Record<string, unknown>): Promise<ApiResponse<QueryTrajectoryResponse>> {
    const resp = await apiClient.get(`/fleet/${fleetId}/trajectory`, { params })
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: queryTrajectoryResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** GET /api/v1/fleet/{fleetId}/high-risk-drivers */
  async drillDownHighRisk(fleetId: string, params?: Record<string, unknown>): Promise<ApiResponse<DrillDownHighRiskResponse>> {
    const resp = await apiClient.get(`/fleet/${fleetId}/high-risk-drivers`, { params })
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: drillDownHighRiskResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** POST /api/v1/fleet/reports */
  async generateReport(body: Record<string, unknown>): Promise<ApiResponse<GenerateReportResponse>> {
    const resp = await apiClient.post('/fleet/reports', body)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: generateReportResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** GET /api/v1/fleet/reports/{reportId}/download — 返回 Blob，不走 fromJson */
  async downloadReport(reportId: string, format: string): Promise<ApiResponse<Blob>> {
    return apiClient.downloadBlob(`/fleet/reports/${reportId}/download`, { params: { format } })
  }

  /** POST /api/v1/fleet/performance-warning-subscription */
  async subscribePerformanceWarning(body: Record<string, unknown>): Promise<ApiResponse<SubscribePerformanceWarningResponse>> {
    const resp = await apiClient.post('/fleet/performance-warning-subscription', body)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: subscribePerformanceWarningResponseFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** DELETE /api/v1/fleet/performance-warning-subscription/{subscriptionId} — 仅返回成功状态，无响应体 */
  async unsubscribePerformanceWarning(subscriptionId: string): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.delete(`/fleet/performance-warning-subscription/${subscriptionId}`)
  }
}

export const fleetApi = new FleetApi()
