/**
 * 车载安全监测系统 — 远程监护服务 DTO（S3）
 *
 * 基于 docs/ood_interface.md §1.3 RemoteGuardianshipService
 *
 * fromJson 构造器：ArkTS-safe，仅用基础类型断言逐字段提取，
 * 使 API 层可返回具体 DTO 类型而非 ApiResponse<Record<string, Object>>（问题 4 修复）。
 */

import { getStr, getNum, getArray, getRecord } from '../common/JsonParser'
import type {
  CareRelationshipStatus,
  GuardianshipPermissionType,
  MediaSessionType,
  RescueRequestStatus,
  RiskLevel,
  SparkRTCRole,
  WindowControlOperation,
  WindowPosition,
  WindowState,
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

/** ArkTS-safe 构造器 */
export function requestMediaSessionRespFromJson(raw: Record<string, Object>): RequestMediaSessionResp {
  return {
    sessionHandle: getStr(raw, 'sessionHandle'),
    sessionToken: getStr(raw, 'sessionToken'),
    sparkRTCRoomId: getStr(raw, 'sparkRTCRoomId'),
    sparkRTCJoinToken: getStr(raw, 'sparkRTCJoinToken'),
  }
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

/** ArkTS-safe 构造器 */
export function triggerManualRescueRespFromJson(raw: Record<string, Object>): TriggerManualRescueResp {
  return {
    rescueRequestId: getStr(raw, 'rescueRequestId'),
    rescueReportId: getStr(raw, 'rescueReportId'),
    status: getStr(raw, 'status') as RescueRequestStatus,
  }
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

/** ArkTS-safe 构造器 */
export function queryWindowStatusRespFromJson(raw: Record<string, Object>): QueryWindowStatusResp {
  const arr = getArray(raw, 'windowStatuses')
  const windowStatuses: WindowStatusEntry[] = []
  for (let i = 0; i < arr.length; i++) {
    const item = arr[i]
    windowStatuses.push({
      windowPosition: getStr(item, 'windowPosition') as WindowPosition,
      state: getStr(item, 'state') as WindowState,
      updatedAt: getStr(item, 'updatedAt'),
    })
  }
  return { windowStatuses }
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

export function guardianshipPermissionEntryFromJson(raw: Record<string, Object>): GuardianshipPermissionEntry {
  return {
    permissionType: getStr(raw, 'permissionType') as GuardianshipPermissionType,
    granted: (raw['granted'] !== undefined && raw['granted'] !== null) ? (raw['granted'] as boolean) : false,
    grantedAt: getStr(raw, 'grantedAt'),
    expiresAt: (raw['expiresAt'] !== undefined && raw['expiresAt'] !== null) ? getStr(raw, 'expiresAt') : undefined,
  }
}

export function careRelationshipSummaryFromJson(raw: Record<string, Object>): CareRelationshipSummary {
  return {
    status: getStr(raw, 'status') as CareRelationshipStatus,
    establishedAt: getStr(raw, 'establishedAt'),
  }
}

export function queryGuardianshipPermissionsRespFromJson(raw: Record<string, Object>): QueryGuardianshipPermissionsResp {
  const arr = getArray(raw, 'permissions')
  const permissions: GuardianshipPermissionEntry[] = []
  for (let i = 0; i < arr.length; i++) {
    permissions.push(guardianshipPermissionEntryFromJson(arr[i]))
  }
  return {
    familyAccountId: getStr(raw, 'familyAccountId'),
    driverId: getStr(raw, 'driverId'),
    permissions,
    careRelationship: careRelationshipSummaryFromJson(getRecord(raw, 'careRelationship')),
  }
}

export interface IssueSparkRTCTokenResp {
  token: string
  expiresAt: string  // ISO 8601（有效期 10 分钟）
}

export function issueSparkRTCTokenRespFromJson(raw: Record<string, Object>): IssueSparkRTCTokenResp {
  return {
    token: getStr(raw, 'token'),
    expiresAt: getStr(raw, 'expiresAt'),
  }
}
