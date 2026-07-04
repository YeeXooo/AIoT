/**
 * 车载安全监测系统 — 车队管理 DTO（S4）
 *
 * 基于 docs/ood_interface.md §1.4 FleetManagementService
 *
 * fromJson 构造器：ArkTS-safe，仅用基础类型断言逐字段提取，
 * 使 API 层可返回具体 DTO 类型而非 ApiResponse<Record<string, Object>>（问题 4 修复）。
 */

import { getStr, getNum, getArray, getRecord } from '../common/JsonParser'
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

/** ArkTS-safe 构造器 */
export function heatmapPointFromJson(raw: Record<string, Object>): HeatmapPoint {
  return {
    latitude: getNum(raw, 'latitude'),
    longitude: getNum(raw, 'longitude'),
    riskIntensity: getNum(raw, 'riskIntensity'),
  }
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

/** ArkTS-safe 构造器 */
export function getFatigueDistributionResponseFromJson(raw: Record<string, Object>): GetFatigueDistributionResponse {
  const distRaw = getRecord(raw, 'distribution')
  const heatArr = getArray(raw, 'heatmapData')
  const heatmapData: HeatmapPoint[] = []
  for (let i = 0; i < heatArr.length; i++) {
    heatmapData.push(heatmapPointFromJson(heatArr[i]))
  }
  return {
    distribution: {
      L1_HINT: getNum(distRaw, 'L1_HINT'),
      L2_WARNING: getNum(distRaw, 'L2_WARNING'),
      L3_CRITICAL: getNum(distRaw, 'L3_CRITICAL'),
    },
    heatmapData,
    dataFreshness: getStr(raw, 'dataFreshness') as DataFreshness,
    generatedAt: getStr(raw, 'generatedAt'),
  }
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

/** ArkTS-safe 构造器 */
export function offlineVehicleEntryFromJson(raw: Record<string, Object>): OfflineVehicleEntry {
  return {
    vehicleId: getStr(raw, 'vehicleId'),
    licensePlate: getStr(raw, 'licensePlate'),
    driverId: getStr(raw, 'driverId'),
    driverName: getStr(raw, 'driverName'),
    offlineReason: getStr(raw, 'offlineReason') as OfflineReason,
    offlineSince: getStr(raw, 'offlineSince'),
    lastHeartbeat: getStr(raw, 'lastHeartbeat'),
  }
}

export interface GetOfflineVehiclesResponse {
  offlineVehicles: OfflineVehicleEntry[]
}

/** ArkTS-safe 构造器 */
export function getOfflineVehiclesResponseFromJson(raw: Record<string, Object>): GetOfflineVehiclesResponse {
  const arr = getArray(raw, 'offlineVehicles')
  const offlineVehicles: OfflineVehicleEntry[] = []
  for (let i = 0; i < arr.length; i++) {
    offlineVehicles.push(offlineVehicleEntryFromJson(arr[i]))
  }
  return { offlineVehicles }
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

/** ArkTS-safe 构造器 */
export function trajectoryPointFromJson(raw: Record<string, Object>): TrajectoryPoint {
  return {
    timestamp: getStr(raw, 'timestamp'),
    latitude: getNum(raw, 'latitude'),
    longitude: getNum(raw, 'longitude'),
    speed: getNum(raw, 'speed'),
  }
}

export interface QueryTrajectoryResponse {
  trajectoryPoints: TrajectoryPoint[]
  totalCount: number
  dataConsistency: DataConsistency
}

/** ArkTS-safe 构造器 */
export function queryTrajectoryResponseFromJson(raw: Record<string, Object>): QueryTrajectoryResponse {
  const arr = getArray(raw, 'trajectoryPoints')
  const trajectoryPoints: TrajectoryPoint[] = []
  for (let i = 0; i < arr.length; i++) {
    trajectoryPoints.push(trajectoryPointFromJson(arr[i]))
  }
  return {
    trajectoryPoints,
    totalCount: getNum(raw, 'totalCount'),
    dataConsistency: getStr(raw, 'dataConsistency') as DataConsistency,
  }
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

/** ArkTS-safe 构造器 */
export function latestTripSummaryFromJson(raw: Record<string, Object>): LatestTripSummary {
  return {
    tripId: getStr(raw, 'tripId'),
    startTime: getStr(raw, 'startTime'),
    endTime: getStr(raw, 'endTime'),
    score: getNum(raw, 'score'),
  }
}

export interface HighRiskDriverEntry {
  driverId: string
  driverName: string
  compositeRiskScore: number
  latestTripSummary: LatestTripSummary
  primaryPenaltyItems: string[]
}

/** ArkTS-safe 构造器 */
export function highRiskDriverEntryFromJson(raw: Record<string, Object>): HighRiskDriverEntry {
  const penaltyArr = getArray(raw, 'primaryPenaltyItems')
  const primaryPenaltyItems: string[] = []
  for (let i = 0; i < penaltyArr.length; i++) {
    primaryPenaltyItems.push(String(penaltyArr[i]))
  }
  return {
    driverId: getStr(raw, 'driverId'),
    driverName: getStr(raw, 'driverName'),
    compositeRiskScore: getNum(raw, 'compositeRiskScore'),
    latestTripSummary: latestTripSummaryFromJson(getRecord(raw, 'latestTripSummary')),
    primaryPenaltyItems,
  }
}

export interface DrillDownHighRiskResponse {
  drivers: HighRiskDriverEntry[]
  totalCount: number
}

/** ArkTS-safe 构造器 */
export function drillDownHighRiskResponseFromJson(raw: Record<string, Object>): DrillDownHighRiskResponse {
  const arr = getArray(raw, 'drivers')
  const drivers: HighRiskDriverEntry[] = []
  for (let i = 0; i < arr.length; i++) {
    drivers.push(highRiskDriverEntryFromJson(arr[i]))
  }
  return {
    drivers,
    totalCount: getNum(raw, 'totalCount'),
  }
}

// ===================================================================
// 驾驶行为报告
// ===================================================================

export interface TimeRange {
  start: string  // ISO 8601
  end: string    // ISO 8601
}

/** ArkTS-safe 构造器 */
export function timeRangeFromJson(raw: Record<string, Object>): TimeRange {
  return {
    start: getStr(raw, 'start'),
    end: getStr(raw, 'end'),
  }
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

/** ArkTS-safe 构造器 */
export function subScoresFromJson(raw: Record<string, Object>): SubScores {
  return {
    fatigueScore: getNum(raw, 'fatigueScore'),
    distractionScore: getNum(raw, 'distractionScore'),
    abnormalDrivingScore: getNum(raw, 'abnormalDrivingScore'),
  }
}

export interface DrivingBehaviorSummary {
  overallScore: number
  subScores: SubScores
  trendVsLastPeriod: number
}

/** ArkTS-safe 构造器 */
export function drivingBehaviorSummaryFromJson(raw: Record<string, Object>): DrivingBehaviorSummary {
  return {
    overallScore: getNum(raw, 'overallScore'),
    subScores: subScoresFromJson(getRecord(raw, 'subScores')),
    trendVsLastPeriod: getNum(raw, 'trendVsLastPeriod'),
  }
}

export interface RiskDistribution {
  FATIGUE: number
  DISTRACTION: number
  ROAD_RAGE: number
}

/** ArkTS-safe 构造器 */
export function riskDistributionFromJson(raw: Record<string, Object>): RiskDistribution {
  return {
    FATIGUE: getNum(raw, 'FATIGUE'),
    DISTRACTION: getNum(raw, 'DISTRACTION'),
    ROAD_RAGE: getNum(raw, 'ROAD_RAGE'),
  }
}

export interface PenaltyBreakdownEntry {
  category: string
  penaltyScore: number
  topViolations: string[]
}

/** ArkTS-safe 构造器 */
export function penaltyBreakdownEntryFromJson(raw: Record<string, Object>): PenaltyBreakdownEntry {
  const vArr = getArray(raw, 'topViolations')
  const topViolations: string[] = []
  for (let i = 0; i < vArr.length; i++) {
    topViolations.push(String(vArr[i]))
  }
  return {
    category: getStr(raw, 'category'),
    penaltyScore: getNum(raw, 'penaltyScore'),
    topViolations,
  }
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

/** ArkTS-safe 构造器 */
export function reportDataFromJson(raw: Record<string, Object>): ReportData {
  const pArr = getArray(raw, 'penaltyBreakdown')
  const penaltyBreakdown: PenaltyBreakdownEntry[] = []
  for (let i = 0; i < pArr.length; i++) {
    penaltyBreakdown.push(penaltyBreakdownEntryFromJson(pArr[i]))
  }
  return {
    reportId: getStr(raw, 'reportId'),
    driverId: getStr(raw, 'driverId'),
    timeRange: timeRangeFromJson(getRecord(raw, 'timeRange')),
    reportType: getStr(raw, 'reportType') as ReportType,
    drivingBehaviorSummary: drivingBehaviorSummaryFromJson(getRecord(raw, 'drivingBehaviorSummary')),
    riskDistribution: riskDistributionFromJson(getRecord(raw, 'riskDistribution')),
    penaltyBreakdown,
    totalMileage: getNum(raw, 'totalMileage'),
    totalDrivingTime: getStr(raw, 'totalDrivingTime'),
    generatedAt: getStr(raw, 'generatedAt'),
  }
}

export interface GenerateReportResponse {
  reportId: string
  reportData: ReportData
  downloadUrl: string
  isEmpty: boolean
}

/** ArkTS-safe 构造器 */
export function generateReportResponseFromJson(raw: Record<string, Object>): GenerateReportResponse {
  return {
    reportId: getStr(raw, 'reportId'),
    reportData: reportDataFromJson(getRecord(raw, 'reportData')),
    downloadUrl: getStr(raw, 'downloadUrl'),
    isEmpty: (raw['isEmpty'] ?? false) as boolean,
  }
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

/** ArkTS-safe 构造器 */
export function subscribePerformanceWarningResponseFromJson(raw: Record<string, Object>): SubscribePerformanceWarningResponse {
  return {
    subscriptionId: getStr(raw, 'subscriptionId'),
  }
}
