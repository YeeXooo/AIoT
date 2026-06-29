/**
 * 车载安全监测系统 — 驾驶员 / 风险监测 DTO（S1）
 *
 * 基于 docs/ood_interface.md §1.1 RiskMonitoringService
 */

import type { AlertType, RiskLevel, StatusColor, GeoPoint } from './types'

// ===================================================================
// GetDriverRiskStatusResponse
// ===================================================================

export interface ActiveAlertEntry {
  alertType: AlertType
  riskLevel: RiskLevel
}

export interface GetDriverRiskStatusResponse {
  hasActiveTrip: boolean
  activeAlerts: ActiveAlertEntry[]
  derivedStatusColor: StatusColor
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

export interface QueryAlertHistoryResponse {
  alerts: AlertSummary[]
  totalCount: number
}
