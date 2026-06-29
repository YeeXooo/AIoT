/**
 * 车载安全监测系统 — 车队管理 DTO（S4）
 *
 * 基于 docs/ood_interface.md §1.4 FleetManagementService
 */

import type {
  DataConsistency,
  DataFreshness,
  OfflineReason,
  ReportType
} from './types'

// ===================================================================
// 疲劳分布看板
// ===================================================================

export interface HeatmapPoint {
  latitude: number
  longitude: number
  riskIntensity: number
}

export interface GetFatigueDistributionResponse {
  /** 各等级占比（小数，总和为 1.0） */
  distribution: {
    L1_HINT: number
    L2_WARNING: number
    L3_CRITICAL: number
  }
  heatmapData: HeatmapPoint[]
  dataFreshness: DataFreshness
  generatedAt: string  // ISO 8601
}

// ===================================================================
// 脱线车辆列表
// ===================================================================

export interface OfflineVehicleEntry {
  vehicleId: string
  licensePlate: string
  driverId: string
  driverName: string
  offlineReason: OfflineReason
  offlineSince: string   // ISO 8601
  lastHeartbeat: string  // ISO 8601
}

export interface GetOfflineVehiclesResponse {
  offlineVehicles: OfflineVehicleEntry[]
}

// ===================================================================
// 车辆轨迹查询
// ===================================================================

export interface TrajectoryPoint {
  timestamp: string  // ISO 8601
  latitude: number
  longitude: number
  speed: number    // km/h
}

export interface QueryTrajectoryResponse {
  trajectoryPoints: TrajectoryPoint[]
  totalCount: number
  dataConsistency: DataConsistency
}

// ===================================================================
// 高风险司机钻取
// ===================================================================

export interface LatestTripSummary {
  tripId: string
  startTime: string
  endTime: string
  score: number
}

export interface HighRiskDriverEntry {
  driverId: string
  driverName: string
  compositeRiskScore: number
  latestTripSummary: LatestTripSummary
  primaryPenaltyItems: string[]
}

export interface DrillDownHighRiskResponse {
  drivers: HighRiskDriverEntry[]
  totalCount: number
}

// ===================================================================
// 驾驶行为报告
// ===================================================================

export interface TimeRange {
  start: string  // ISO 8601
  end: string    // ISO 8601
}

export interface GenerateReportRequest {
  driverId: string
  timeRange: TimeRange
  reportType: ReportType
}

export interface SubScores {
  fatigueScore: number
  distractionScore: number
  abnormalDrivingScore: number
}

export interface DrivingBehaviorSummary {
  overallScore: number
  subScores: SubScores
  trendVsLastPeriod: number
}

export interface RiskDistribution {
  FATIGUE: number
  DISTRACTION: number
  ROAD_RAGE: number
}

export interface PenaltyBreakdownEntry {
  category: string
  penaltyScore: number
  topViolations: string[]
}

export interface ReportData {
  reportId: string
  driverId: string
  timeRange: TimeRange
  reportType: ReportType
  drivingBehaviorSummary: DrivingBehaviorSummary
  riskDistribution: RiskDistribution
  penaltyBreakdown: PenaltyBreakdownEntry[]
  totalMileage: number
  totalDrivingTime: string  // ISO 8601 duration
  generatedAt: string       // ISO 8601
}

export interface GenerateReportResponse {
  reportId: string
  reportData: ReportData
  downloadUrl: string
  isEmpty: boolean
}

// ===================================================================
// 绩效预警订阅
// ===================================================================

export interface SubscribePerformanceWarningRequest {
  /** 车队管理员账户 ID（须与 JWT sub 一致） */
  adminId: string
  /** 目标车队 ID */
  fleetId: string
}

export interface SubscribePerformanceWarningResponse {
  subscriptionId: string
}
