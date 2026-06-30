/**
 * 车载安全监测系统 — 驾驶员 / 风险监测 DTO（S1）
 *
 * 基于 docs/ood_interface.md §1.1 RiskMonitoringService
 *
 * fromJson 构造器：ArkTS-safe，仅用基础类型断言逐字段提取，
 * 使 API 层可返回具体 DTO 类型而非 ApiResponse<Record<string, unknown>>（问题 4 修复）。
 */

import { getStr, getNum, getBool, getArray, getRecord } from '../common/JsonParser'
import type { AlertType, RiskLevel, StatusColor, GeoPoint } from './types'

// ===================================================================
// GetDriverRiskStatusResponse
// ===================================================================

export interface ActiveAlertEntry {
  alertType: AlertType
  riskLevel: RiskLevel
}

/** ArkTS-safe 构造器：从原始 JSON 构建 ActiveAlertEntry */
export function activeAlertEntryFromJson(raw: Record<string, unknown>): ActiveAlertEntry {
  return {
    alertType: getStr(raw, 'alertType') as AlertType,
    riskLevel: getStr(raw, 'riskLevel') as RiskLevel,
  }
}

export interface GetDriverRiskStatusResponse {
  hasActiveTrip: boolean
  activeAlerts: ActiveAlertEntry[]
  derivedStatusColor: StatusColor
}

/** ArkTS-safe 构造器：从原始 JSON 构建 GetDriverRiskStatusResponse */
export function getDriverRiskStatusResponseFromJson(raw: Record<string, unknown>): GetDriverRiskStatusResponse {
  const alertArr = getArray(raw, 'activeAlerts')
  const activeAlerts: ActiveAlertEntry[] = []
  for (let i = 0; i < alertArr.length; i++) {
    activeAlerts.push(activeAlertEntryFromJson(alertArr[i]))
  }
  return {
    hasActiveTrip: getBool(raw, 'hasActiveTrip'),
    activeAlerts,
    derivedStatusColor: getStr(raw, 'derivedStatusColor') as StatusColor,
  }
}

// ===================================================================
// QueryAlertHistoryResponse
// ===================================================================

export interface AlertSummary {
  alertId: string
  alertType: AlertType
  riskLevel: RiskLevel
  occurredAt: string  // ISO 8601
  resolvedAt?: string // ISO 8601
  tripId: string
  gpsLocation?: GeoPoint
}

/** ArkTS-safe 构造器：从原始 JSON 构建 AlertSummary */
export function alertSummaryFromJson(raw: Record<string, unknown>): AlertSummary {
  const gpsOpt = (raw['gpsLocation'] !== undefined && raw['gpsLocation'] !== null)
    ? (getRecord(raw, 'gpsLocation') as unknown as GeoPoint)
    : undefined
  return {
    alertId: getStr(raw, 'alertId'),
    alertType: getStr(raw, 'alertType') as AlertType,
    riskLevel: getStr(raw, 'riskLevel') as RiskLevel,
    occurredAt: getStr(raw, 'occurredAt'),
    resolvedAt: (raw['resolvedAt'] !== undefined && raw['resolvedAt'] !== null)
      ? getStr(raw, 'resolvedAt')
      : undefined,
    tripId: getStr(raw, 'tripId'),
    gpsLocation: gpsOpt,
  }
}

export interface QueryAlertHistoryResponse {
  alerts: AlertSummary[]
  totalCount: number
}

/** ArkTS-safe 构造器：从原始 JSON 构建 QueryAlertHistoryResponse */
export function queryAlertHistoryResponseFromJson(raw: Record<string, unknown>): QueryAlertHistoryResponse {
  const arr = getArray(raw, 'alerts')
  const alerts: AlertSummary[] = []
  for (let i = 0; i < arr.length; i++) {
    alerts.push(alertSummaryFromJson(arr[i]))
  }
  return {
    alerts,
    totalCount: getNum(raw, 'totalCount'),
  }
}
