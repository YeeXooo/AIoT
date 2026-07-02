/**
 * 车载安全监测系统 — 前端基础类型定义
 *
 * 基于 docs/ood_interface.md §4.1 ArkTS 数据模型定义
 * 所有类型均严格遵循接口层 OOD 契约
 */

// ===================================================================
// 枚举 / 字面量类型别名
// ===================================================================

/** 告警类型 */
export type AlertType =
  | 'FATIGUE'
  | 'DISTRACTION'
  | 'ROAD_RAGE'
  | 'LIFE_DETECTION'
  | 'COLLISION_DISABILITY'
  | 'PERFORMANCE_WARNING'

/** 风险等级 */
export type RiskLevel = 'L1_HINT' | 'L2_WARNING' | 'L3_CRITICAL'

/** 驾驶员综合状态色（三值枚举，与领域层 VO-15 一致） */
export type StatusColor = 'GREEN' | 'YELLOW' | 'RED'

/** 媒体会话类型 */
export type MediaSessionType = 'AUDIO' | 'VIDEO'

/** 手动救援请求状态 */
export type RescueRequestStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

/** 车窗操作类型 */
export type WindowControlOperation = 'OPEN' | 'CLOSE' | 'PARTIAL_OPEN'

/** 车窗位置 */
export type WindowPosition = 'FRONT_LEFT' | 'FRONT_RIGHT' | 'REAR_LEFT' | 'REAR_RIGHT'

/** 车窗状态 */
export type WindowState = 'OPEN' | 'CLOSED' | 'PARTIAL' | 'UNKNOWN' | 'MOVING'

/** 车窗操作结果 */
export type WindowOperationResult = 'SUCCESS' | 'TIMEOUT' | 'FAILED' | 'PENDING'

/** 家属监护权限类型 */
export type GuardianshipPermissionType =
  | 'MEDIA_CALL'
  | 'WINDOW_CONTROL'
  | 'MANUAL_RESCUE'
  | 'STATUS_MONITORING'

/** 监护关系状态 */
export type CareRelationshipStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED'

/** SparkRTC 房间角色 */
export type SparkRTCRole = 'subscriber' | 'publisher'

/** 行程状态 */
export type TripStatus = 'NOT_STARTED' | 'ACTIVE' | 'COMPLETED'

/** 家属权限授予原因 */
export type AccessGrantReason =
  | 'REGULAR_60S'
  | 'EMERGENCY_ACTIVATION'
  | 'OCCLUSION_RECOVERY'

/** 家属权限撤销原因 */
export type AccessRevokeReason =
  | 'RISK_DECLINED'
  | 'CAMERA_OCCLUDED'
  | 'DRIVER_DEACTIVATED'

/** 认证方式 */
export type AuthMethod = 'PASSWORD' | 'SMS_CODE'

/** 二次身份验证方式 */
export type SecondaryVerifyMethod = 'OTP' | 'BIOMETRIC'

/** 账户角色 */
export type AccountRole = 'FAMILY' | 'MANAGER' | 'RESCUE'

/** 数据新鲜度 */
export type DataFreshness = 'FRESH' | 'STALE'

/** 脱线原因 */
export type OfflineReason = 'SENSOR_FAULT' | 'COMMUNICATION_LOST'

/** 数据一致性 */
export type DataConsistency = 'CONSISTENT' | 'INCONSISTENT'

/** 报告类型 */
export type ReportType = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY'

/** 告警触发类型（S5） */
export type RescueTriggerType = 'COLLISION_DISABILITY' | 'MANUAL' | 'LIFE_DETECTION'

/** 救援记录状态（S5） */
export type RescueRecordStatus =
  | 'SENT'
  | 'CONFIRMED'
  | 'PENDING_RETRY'
  | 'MANUAL_ESCALATION'

/** 升级进度阶段 */
export type UpgradeStage =
  | 'PENDING'
  | 'TRANSMITTING'
  | 'VERIFYING'
  | 'READY'
  | 'UPGRADING'
  | 'COMPLETED'
  | 'ROLLING_BACK'
  | 'ROLLED_BACK'

/** 绩效评分周期 */
export type ScorePeriod = 'trip' | 'weekly' | 'monthly' | 'quarterly'

/** GPS 坐标点 */
export interface GeoPoint {
  latitude: number
  longitude: number
}

/** 车窗状态条目（通用类型，用于 WebSocket 推送和 REST 响应） */
export interface WindowStatusEntry {
  windowPosition: WindowPosition
  state: WindowState
  lastOperation?: WindowControlOperation
  lastOperationResult?: WindowOperationResult
  updatedAt: string  // ISO 8601
}
