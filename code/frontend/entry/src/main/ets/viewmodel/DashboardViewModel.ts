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

function mockDashboardData(): DashboardData {
  return {
    statusColor: 'YELLOW',
    hasActiveTrip: true,
    activeAlerts: [
      { alertType: 'FATIGUE', riskLevel: 'L2_WARNING' },
      { alertType: 'DISTRACTION', riskLevel: 'L1_HINT' },
    ],
    alertHistory: [
      { alertId: 'a1', alertType: 'FATIGUE', riskLevel: 'L2_WARNING', occurredAt: '2026-07-03T10:30:00Z', tripId: 't1' },
      { alertId: 'a2', alertType: 'DISTRACTION', riskLevel: 'L1_HINT', occurredAt: '2026-07-03T09:15:00Z', tripId: 't1' },
      { alertId: 'a3', alertType: 'ROAD_RAGE', riskLevel: 'L3_CRITICAL', occurredAt: '2026-07-02T18:45:00Z', tripId: 't0' },
    ],
    totalAlerts: 3,
  }
}

export class DashboardViewModel {
  driverId: string = 'd1'

  async load(): Promise<ViewState<DashboardData>> {
    try {
      const riskResp = await driverApi.getRiskStatus(this.driverId)
      if (!riskResp.success || riskResp.data === undefined) {
        return successState<DashboardData>(mockDashboardData())
      }

      const alertResp = await driverApi.queryAlertHistory(this.driverId)
      if (!alertResp.success || alertResp.data === undefined) {
        return successState<DashboardData>(mockDashboardData())
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
      return successState<DashboardData>(mockDashboardData())
    }
  }
}

export const dashboardVM = new DashboardViewModel()
