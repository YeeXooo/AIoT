/**
 * Dashboard VM — 驾驶员风险状态 + 告警历史。
 *
 * 注意：DriverApi 依赖的 ApiClient 使用 fetch/AbortController（ArkTS 不可用），
 * 故本 VM 暂返回 mock 数据，待 ApiClient 改用 @kit.NetworkKit.http 后接入真实接口。
 */
import { ViewState, successState } from './ViewState'

/** 状态色（与 model/types 解耦） */
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

function mockData(): DashboardData {
  return {
    statusColor: 'GREEN',
    hasActiveTrip: true,
    activeAlerts: [],
    alertHistory: [
      { alertId: '1', alertType: 'FATIGUE', riskLevel: 'L1_HINT', occurredAt: '2026-07-01T14:32:00Z', tripId: 'T001' },
      { alertId: '2', alertType: 'DISTRACTION', riskLevel: 'L2_WARNING', occurredAt: '2026-07-01T13:15:00Z', tripId: 'T001' },
      { alertId: '3', alertType: 'FATIGUE', riskLevel: 'L1_HINT', occurredAt: '2026-07-01T11:40:00Z', tripId: 'T001' },
    ],
    totalAlerts: 3,
  }
}

export class DashboardViewModel {
  async load(): Promise<ViewState<DashboardData>> {
    return successState<DashboardData>(mockData())
  }
}

export const dashboardVM = new DashboardViewModel()
