/**
 * 车载安全监测系统 — 远程监护 API（S3，ArkTS 兼容）
 *
 * 问题 4 修复：API 层返回具体 DTO 类型而非 ApiResponse<Record<string, Object>>。
 * 仅对有 fromJson 构造器的响应 DTO 接入类型化返回；其余暂保留 Record 返回，
 * 待后续补齐 fromJson 构造器后再升级（避免半成品类型断言）。
 */

import { apiClient, type ApiResponse } from './ApiClient'
import {
  requestMediaSessionRespFromJson,
  triggerManualRescueRespFromJson,
  queryWindowStatusRespFromJson,
  queryGuardianshipPermissionsRespFromJson,
  issueSparkRTCTokenRespFromJson,
} from '../model/guardianship'
import type {
  RequestMediaSessionResp,
  TriggerManualRescueResp,
  QueryWindowStatusResp,
  QueryGuardianshipPermissionsResp,
  IssueSparkRTCTokenResp,
} from '../model/guardianship'

export class GuardianshipApi {
  /** POST /api/v1/guardianship/media-session */
  async requestMediaSession(body: Record<string, Object>): Promise<ApiResponse<RequestMediaSessionResp>> {
    const resp = await apiClient.post('/guardianship/media-session', body)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: requestMediaSessionRespFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** DELETE /api/v1/guardianship/media-session/{sessionHandle} — 仅返回成功状态，无响应体 */
  async endMediaSession(sessionHandle: string): Promise<ApiResponse<Record<string, Object>>> {
    return apiClient.delete(`/guardianship/media-session/${sessionHandle}`)
  }

  /** PUT /api/v1/guardianship/notification-preference — 仅返回成功状态，无响应体 */
  async updateNotificationPreference(body: Record<string, Object>): Promise<ApiResponse<Record<string, Object>>> {
    return apiClient.put('/guardianship/notification-preference', body)
  }

  /** POST /api/v1/guardianship/manual-rescue */
  async triggerManualRescue(body: Record<string, Object>): Promise<ApiResponse<TriggerManualRescueResp>> {
    const resp = await apiClient.post('/guardianship/manual-rescue', body)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: triggerManualRescueRespFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** POST /api/v1/guardianship/window-control — 仅返回成功状态，无响应体 */
  async controlVehicleWindow(body: Record<string, Object>): Promise<ApiResponse<Record<string, Object>>> {
    return apiClient.post('/guardianship/window-control', body)
  }

  /** GET /api/v1/vehicles/{vehicleId}/windows */
  async queryWindowStatus(vehicleId: string): Promise<ApiResponse<QueryWindowStatusResp>> {
    const resp = await apiClient.get(`/vehicles/${vehicleId}/windows`)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: queryWindowStatusRespFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /** GET /api/v1/guardianship/{driverId}/permissions */
  async queryGuardianshipPermissions(driverId: string): Promise<ApiResponse<QueryGuardianshipPermissionsResp>> {
    const resp = await apiClient.get(`/guardianship/${driverId}/permissions`)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: queryGuardianshipPermissionsRespFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }

  /**
   * GET /api/v1/guardianship/list?driverId=&accountId=
   * 查询监护绑定列表。FAMILY 账号传入 accountId 过滤当前账户的绑定；
   * driverId 可选，传入则只查该驾驶员的绑定。
   */
  async queryBoundDrivers(accountId: string, driverId?: string): Promise<ApiResponse<Record<string, Object>>> {
    const params: Record<string, Object> = { 'accountId': accountId }
    if (driverId !== undefined && driverId.length > 0) {
      params['driverId'] = driverId
    }
    return apiClient.get('/guardianship/list', { params })
  }

  /** POST /api/v1/sparkrtc/token */
  async issueSparkRTCToken(body: Record<string, Object>): Promise<ApiResponse<IssueSparkRTCTokenResp>> {
    const resp = await apiClient.post('/sparkrtc/token', body)
    if (resp.success && resp.data !== undefined) {
      return {
        success: true,
        data: issueSparkRTCTokenRespFromJson(resp.data),
        status: resp.status,
      }
    }
    return { success: false, error: resp.error, status: resp.status }
  }
}

export const guardianshipApi = new GuardianshipApi()
