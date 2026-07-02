/**
 * Fleet VM — 车队概览 / 疲劳分布 / 脱线车辆。
 *
 * 注意：FleetApi 依赖的 ApiClient 暂不可用，本 VM 返回 mock。
 */
import { ViewState, successState } from './ViewState'

export type OfflineReason = 'SENSOR_FAULT' | 'COMMUNICATION_LOST'
export type DataFreshness = 'FRESH' | 'STALE'

export interface FatigueDistribution {
  L1_HINT: number
  L2_WARNING: number
  L3_CRITICAL: number
}

export interface FatigueData {
  distribution: FatigueDistribution
  dataFreshness: DataFreshness
  generatedAt: string
}

export interface OfflineVehicleEntry {
  vehicleId: string
  licensePlate: string
  driverId: string
  driverName: string
  offlineReason: OfflineReason
  offlineSince: string
  lastHeartbeat: string
}

export interface FleetData {
  totalVehicles: number
  offlineCount: number
  highRiskCount: number
  fatigue: FatigueData
  offlineVehicles: OfflineVehicleEntry[]
}

function mockData(): FleetData {
  return {
    totalVehicles: 48,
    offlineCount: 3,
    highRiskCount: 2,
    fatigue: {
      distribution: { L1_HINT: 0.62, L2_WARNING: 0.28, L3_CRITICAL: 0.10 },
      dataFreshness: 'FRESH',
      generatedAt: '2026-07-01T18:00:00Z',
    },
    offlineVehicles: [
      { vehicleId: 'v2', licensePlate: '京B·33217', driverId: 'd2', driverName: '李强', offlineReason: 'COMMUNICATION_LOST', offlineSince: '2026-07-01T15:20:00Z', lastHeartbeat: '2026-07-01T15:19:30Z' },
      { vehicleId: 'v4', licensePlate: '京D·55990', driverId: 'd4', driverName: '赵鹏', offlineReason: 'SENSOR_FAULT', offlineSince: '2026-07-01T14:10:00Z', lastHeartbeat: '2026-07-01T14:09:00Z' },
      { vehicleId: 'v5', licensePlate: '京E·12008', driverId: 'd5', driverName: '孙伟', offlineReason: 'COMMUNICATION_LOST', offlineSince: '2026-07-01T13:00:00Z', lastHeartbeat: '2026-07-01T12:59:00Z' },
    ],
  }
}

export class FleetViewModel {
  async load(): Promise<ViewState<FleetData>> {
    return successState<FleetData>(mockData())
  }
}

export const fleetVM = new FleetViewModel()
