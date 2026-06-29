/**
 * 车载安全监测系统 — 远程监护 API（S3，ArkTS 兼容）
 */

import { apiClient, type ApiResponse } from './ApiClient'

export class GuardianshipApi {
  /** POST /api/v1/guardianship/media-session */
  async requestMediaSession(body: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/guardianship/media-session', body)
  }

  /** DELETE /api/v1/guardianship/media-session/{sessionHandle} */
  async endMediaSession(sessionHandle: string): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.delete(`/guardianship/media-session/${sessionHandle}`)
  }

  /** PUT /api/v1/guardianship/notification-preference */
  async updateNotificationPreference(body: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.put('/guardianship/notification-preference', body)
  }

  /** POST /api/v1/guardianship/manual-rescue */
  async triggerManualRescue(body: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/guardianship/manual-rescue', body)
  }

  /** POST /api/v1/guardianship/window-control */
  async controlVehicleWindow(body: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/guardianship/window-control', body)
  }

  /** GET /api/v1/vehicles/{vehicleId}/windows */
  async queryWindowStatus(vehicleId: string): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.get(`/vehicles/${vehicleId}/windows`)
  }

  /** GET /api/v1/guardianship/{driverId}/permissions */
  async queryGuardianshipPermissions(driverId: string): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.get(`/guardianship/${driverId}/permissions`)
  }

  /** POST /api/v1/sparkrtc/token */
  async issueSparkRTCToken(body: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
    return apiClient.post('/sparkrtc/token', body)
  }
}

export const guardianshipApi = new GuardianshipApi()
