/**
 * 车载安全监测系统 — WebSocket 消息模型
 *
 * 基于 docs/ood_interface.md §3.1 家属 APP WebSocket 信令协议
 * 基于 docs/ood_interface.md §4.2 车队大屏 WebSocket 消息模型
 */

import type {
  AccessGrantReason,
  AccessRevokeReason,
  AlertType,
  GeoPoint,
  RiskLevel,
  RescueRequestStatus,
  TripStatus
} from './types'
// 导出基础类型
export type { GeoPoint } from './types'
import type { WindowStatusEntry } from './types'
export type { WindowStatusEntry }

// ===================================================================
// 基础下行消息帧结构
// ===================================================================

export interface WebSocketMessage<T = unknown> {
  type: string
  payload: T
}

// ===================================================================
// 连接管理
// ===================================================================

export interface ConnectionEstablishedMessage {
  connectionId: string
  accountId: string
}

export interface PingMessage {
  serverTime: string
}

// ===================================================================
// 家属状态快照
// ===================================================================

export interface PhysiologicalDigest {
  heartRate: number   // bpm
  spo2: number        // %
  emotionIndex: number // 0.0–1.0
}

export interface DriverStatusSnapshot {
  driverId: string
  vehicleId: string
  timestamp: string  // ISO 8601
  activeAlertLevels: Record<string, RiskLevel>
  gpsLocation?: GeoPoint
  speed?: number
  tripStatus: TripStatus
  physiologicalSummary?: PhysiologicalDigest
  windowStatus?: WindowStatusEntry[]
}

// ===================================================================
// 告警推送
// ===================================================================

export interface AlertTriggeredMessage {
  alertId: string
  alertType: AlertType
  riskLevel: RiskLevel
  occurredAt: string
  resolvedAt?: string
  tripId: string
  gpsLocation?: GeoPoint
}

// ===================================================================
// 权限授予/撤销
// ===================================================================

export interface AccessGrantedMessage {
  driverId: string
  sessionToken: string
  sparkRTCRoomId: string
  sparkRTCJoinToken: string
  reason: AccessGrantReason
}

export interface AccessRevokedMessage {
  driverId: string
  reason: AccessRevokeReason
}

// ===================================================================
// 订阅确认
// ===================================================================

export interface SubscribeStatusAckMessage {
  subscriptionId: string
  initialSnapshot: DriverStatusSnapshot
}

// ===================================================================
// 救援触发确认
// ===================================================================

export interface RescueTriggeredMessage {
  rescueRequestId: string
  rescueReportId: string  // S5 救援报告 ID
  status: RescueRequestStatus
}

// ===================================================================
// SparkRTC Token 续签
// ===================================================================

export interface TokenRenewedMessage {
  sparkRTCRoomId: string
  sparkRTCJoinToken: string
  expiresAt: string  // ISO 8601
}

// ===================================================================
// 错误消息
// ===================================================================

export interface ErrorMessage {
  code: string
  message: string
}

// ===================================================================
// 上行消息请求体类型
// ===================================================================

export interface SubscribeStatusPayload {
  driverId: string
}

export interface UnsubscribeStatusPayload {
  subscriptionId: string
}

export interface RequestMediaPayload {
  familyAccountId: string
  driverId: string
  sessionType: 'AUDIO' | 'VIDEO'
  secondaryAuthToken: string
}

export interface EndMediaPayload {
  sessionHandle: string
}

export interface TriggerRescuePayload {
  familyAccountId: string
  driverId: string
  secondaryAuthToken: string
}

// ===================================================================
// 车队大屏 WebSocket 消息
// ===================================================================

export interface L3AlertMessage {
  fleetId: string
  driverId: string
  vehicleId: string
  alertType: AlertType
  occurredAt: string
  gpsLocation: GeoPoint
}

export interface PerformanceWarningMessage {
  driverId: string
  driverName: string
  score: number
  scorePeriod: string  // 'trip' | 'weekly' | 'monthly' | 'quarterly'
  primaryPenaltyItems: string[]
  occurredAt: string
}
