import { ViewState, successState } from './ViewState'
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
    try {
      const riskResp = await driverApi.getRiskStatus(this.driverId)
      if (!riskResp.success || riskResp.data === undefined) {
        return { state: 'error', data: null, errorMsg: riskResp.error?.message ?? '加载失败' }
      }

      const alertResp = await driverApi.queryAlertHistory(this.driverId)
      if (!alertResp.success || alertResp.data === undefined) {
        return { state: 'error', data: null, errorMsg: alertResp.error?.message ?? '加载失败' }
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
    } catch (e) {
      return { state: 'error', data: null, errorMsg: '加载失败' }
    }
  }
}

export const dashboardVM = new DashboardViewModel()
