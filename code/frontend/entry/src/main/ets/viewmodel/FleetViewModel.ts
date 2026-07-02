import { ViewState, successState } from './ViewState'
import { fleetApi } from '../api/FleetApi'
import type {
  GetFatigueDistributionResponse,
  GetOfflineVehiclesResponse,
  DrillDownHighRiskResponse,
} from '../model/fleet'

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

export class FleetViewModel {
  fleetId: string = 'f1'

  async load(): Promise<ViewState<FleetData>> {
    try {
      const fatigueResp = await fleetApi.getFatigueDistribution(this.fleetId)
      if (!fatigueResp.success || fatigueResp.data === undefined) {
        return { state: 'error', data: null, errorMsg: fatigueResp.error?.message ?? '加载失败' }
      }

      const offlineResp = await fleetApi.getOfflineVehicles(this.fleetId)
      if (!offlineResp.success || offlineResp.data === undefined) {
        return { state: 'error', data: null, errorMsg: offlineResp.error?.message ?? '加载失败' }
      }

      const highRiskResp = await fleetApi.drillDownHighRisk(this.fleetId)
      if (!highRiskResp.success || highRiskResp.data === undefined) {
        return { state: 'error', data: null, errorMsg: highRiskResp.error?.message ?? '加载失败' }
      }

      const fatigueData: GetFatigueDistributionResponse = fatigueResp.data
      const offlineData: GetOfflineVehiclesResponse = offlineResp.data
      const highRiskData: DrillDownHighRiskResponse = highRiskResp.data

      const offlineVehicles: OfflineVehicleEntry[] = []
      for (let i = 0; i < offlineData.offlineVehicles.length; i++) {
        const entry = offlineData.offlineVehicles[i]
        offlineVehicles.push({
          vehicleId: entry.vehicleId,
          licensePlate: entry.licensePlate,
          driverId: entry.driverId,
          driverName: entry.driverName,
          offlineReason: entry.offlineReason,
          offlineSince: entry.offlineSince,
          lastHeartbeat: entry.lastHeartbeat,
        })
      }

      const data: FleetData = {
        totalVehicles: offlineVehicles.length + highRiskData.drivers.length + 40,
        offlineCount: offlineVehicles.length,
        highRiskCount: highRiskData.drivers.length,
        fatigue: {
          distribution: {
            L1_HINT: fatigueData.distribution.L1_HINT,
            L2_WARNING: fatigueData.distribution.L2_WARNING,
            L3_CRITICAL: fatigueData.distribution.L3_CRITICAL,
          },
          dataFreshness: fatigueData.dataFreshness,
          generatedAt: fatigueData.generatedAt,
        },
        offlineVehicles,
      }

      return successState<FleetData>(data)
    } catch (e) {
      return { state: 'error', data: null, errorMsg: '加载失败' }
    }
  }
}

export const fleetVM = new FleetViewModel()
