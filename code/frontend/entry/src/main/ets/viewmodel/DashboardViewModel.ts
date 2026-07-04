import { ViewState, successState, errorState } from './ViewState'
import { driverApi } from '../api/DriverApi'
import type { GetDriverRiskStatusResponse, QueryAlertHistoryResponse } from '../model/driver'
import { sessionStore } from './SessionStore'

export type StatusColor = 'GREEN' | 'YELLOW' | 'RED'
export type RiskLevel = 'L1_HINT' | 'L2_WARNING' | 'L3_CRITICAL'
export type AlertType =
  | 'FATIGUE' | 'DISTRACTION' | 'ROAD_RAGE'
  | 'LIFE_DETECTION' | 'COLLISION_DISABILITY' | 'PERFORMANCE_WARNING'

export interface ActiveAlertEntry {
  alertType: AlertType
  riskLevel: RiskLevel
}

export interface AlertSummary {
  alertId: string
  alertType: AlertType
  riskLevel: RiskLevel
  occurredAt: string
  tripId: string
}

export interface DashboardData {
  statusColor: StatusColor
  hasActiveTrip: boolean
  activeAlerts: ActiveAlertEntry[]
  alertHistory: AlertSummary[]
  totalAlerts: number
}

export class DashboardViewModel {
  driverId: string = 'd1'

  async load(): Promise<ViewState<DashboardData>> {
    // driverId 为空时直接返回 idle，避免拼出 /drivers//risk-status 触发 400
    if (this.driverId.length === 0) {
      return { state: 'idle', data: null, errorMsg: '尚未绑定驾驶员' }
    }
    const riskResp = await driverApi.getRiskStatus(this.driverId)
    if (!riskResp.success || riskResp.data === undefined) {
      const msg = (riskResp.error !== undefined && riskResp.error.message.length > 0)
        ? riskResp.error.message : '获取风险状态失败'
      return errorState<DashboardData>(msg)
    }

    const alertResp = await driverApi.queryAlertHistory(this.driverId)
    if (!alertResp.success || alertResp.data === undefined) {
      const msg = (alertResp.error !== undefined && alertResp.error.message.length > 0)
        ? alertResp.error.message : '获取告警历史失败'
      return errorState<DashboardData>(msg)
    }

    const riskData: GetDriverRiskStatusResponse = riskResp.data
    const alertData: QueryAlertHistoryResponse = alertResp.data

    const activeAlerts: ActiveAlertEntry[] = []
    for (let i = 0; i < riskData.activeAlerts.length; i++) {
      const entry = riskData.activeAlerts[i]
      activeAlerts.push({ alertType: entry.alertType, riskLevel: entry.riskLevel })
    }

    const alertHistory: AlertSummary[] = []
    for (let i = 0; i < alertData.alerts.length; i++) {
      const entry = alertData.alerts[i]
      alertHistory.push({
        alertId: entry.alertId,
        alertType: entry.alertType,
        riskLevel: entry.riskLevel,
        occurredAt: entry.occurredAt,
        tripId: entry.tripId,
      })
    }

    const data: DashboardData = {
      statusColor: riskData.derivedStatusColor,
      hasActiveTrip: riskData.hasActiveTrip,
      activeAlerts,
      alertHistory,
      totalAlerts: alertData.totalCount,
    }

    return successState<DashboardData>(data)
  }
}

export const dashboardVM = new DashboardViewModel()
