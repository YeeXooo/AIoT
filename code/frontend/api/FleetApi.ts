/**
 * 车载安全监测系统 — 车队管理 API（S4，ArkTS 兼容）
 */

import { apiClient, type ApiResponse } from './ApiClient'

export class FleetApi {
  /** GET /api/v1/fleet/{fleetId}/fatigue-distribution */
  async getFatigueDistribution(fleetId: string, params?: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.get(`/fleet/${fleetId}/fatigue-distribution`, { params })
  }

  /** GET /api/v1/fleet/{fleetId}/offline-vehicles */
  async getOfflineVehicles(fleetId: string): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.get(`/fleet/${fleetId}/offline-vehicles`)
  }

  /** GET /api/v1/fleet/{fleetId}/trajectory */
  async queryTrajectory(fleetId: string, params?: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.get(`/fleet/${fleetId}/trajectory`, { params })
  }

  /** GET /api/v1/fleet/{fleetId}/high-risk-drivers */
  async drillDownHighRisk(fleetId: string, params?: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.get(`/fleet/${fleetId}/high-risk-drivers`, { params })
  }

  /** POST /api/v1/fleet/reports */
  async generateReport(body: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/fleet/reports', body)
  }

  /** GET /api/v1/fleet/reports/{reportId}/download */
  async downloadReport(reportId: string, format: string): Promise<ApiResponse<Blob>> {
    return apiClient.downloadBlob(`/fleet/reports/${reportId}/download`, { params: { format } })
  }

  /** POST /api/v1/fleet/performance-warning-subscription */
  async subscribePerformanceWarning(body: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/fleet/performance-warning-subscription', body)
  }

  /** DELETE /api/v1/fleet/performance-warning-subscription/{subscriptionId} */
  async unsubscribePerformanceWarning(subscriptionId: string): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.delete(`/fleet/performance-warning-subscription/${subscriptionId}`)
  }
}

export const fleetApi = new FleetApi()
