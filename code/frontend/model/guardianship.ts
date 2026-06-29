/**
 * 车载安全监测系统 — 远程监护服务 DTO（S3）
 *
 * 基于 docs/ood_interface.md §1.3 RemoteGuardianshipService
 */

import type {
  CareRelationshipStatus,
  GuardianshipPermissionType,
  MediaSessionType,
  RescueRequestStatus,
  RiskLevel,
  SparkRTCRole,
  WindowControlOperation,
  WindowPosition,
  WindowStatusEntry,
} from './types'

// ===================================================================
// 音视频对讲
// ===================================================================

export interface RequestMediaSessionReq {
  familyAccountId: string
  driverId: string
  sessionType: MediaSessionType
  secondaryAuthToken: string
}

export interface RequestMediaSessionResp {
  /** 会话级授权凭证，用于后续媒体会话管理操作（挂断、续期等） */
  sessionHandle: string
  /** 会话级授权凭证 */
  sessionToken: string
  /** SparkRTC 房间 ID */
  sparkRTCRoomId: string
  /** 实际传入 SparkRTC SDK join 方法的入房 Token */
  sparkRTCJoinToken: string
}

// ===================================================================
// 通知偏好
// ===================================================================

export interface UpdateNotificationPreferenceReq {
  familyAccountId: string
  driverId: string
  /** 空数组 = 接收全部等级 */
  preferredRiskLevels: RiskLevel[]
}

// ===================================================================
// 手动救援
// ===================================================================

export interface TriggerManualRescueReq {
  familyAccountId: string
  driverId: string
  secondaryAuthToken: string
}

export interface TriggerManualRescueResp {
  /** S3 内部救援请求 ID */
  rescueRequestId: string
  /** S5 救援报告 ID，用于后续 queryRescueHistory 查询救援进展 */
  rescueReportId: string
  /** 救援请求状态 */
  status: RescueRequestStatus
}

// ===================================================================
// 车窗控制
// ===================================================================

export interface ControlVehicleWindowReq {
  familyAccountId: string
  driverId: string
  windowOperation: WindowControlOperation
  windowPosition: WindowPosition
  secondaryAuthToken: string
}

export interface QueryWindowStatusResp {
  windowStatuses: WindowStatusEntry[]
}

// ===================================================================
// 家属监护权限
// ===================================================================

export interface GuardianshipPermissionEntry {
  permissionType: GuardianshipPermissionType
  granted: boolean
  grantedAt: string     // ISO 8601
  expiresAt?: string    // ISO 8601
}

export interface CareRelationshipSummary {
  status: CareRelationshipStatus
  establishedAt: string  // ISO 8601
}

export interface QueryGuardianshipPermissionsResp {
  familyAccountId: string
  driverId: string
  permissions: GuardianshipPermissionEntry[]
  careRelationship: CareRelationshipSummary
}

// ===================================================================
// SparkRTC Token 签发
// ===================================================================

export interface IssueSparkRTCTokenReq {
  roomId: string
  userId: string
  /** subscriber（家属端，REST 唯一合法值）；publisher 仅通过 MQTT 下发至车机端 */
  role: SparkRTCRole
}

export interface IssueSparkRTCTokenResp {
  token: string
  expiresAt: string  // ISO 8601（有效期 10 分钟）
}
