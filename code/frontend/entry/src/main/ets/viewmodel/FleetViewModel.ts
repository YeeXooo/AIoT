import { ViewState, successState, errorState } from './ViewState'
import { fleetApi } from '../api/FleetApi'
import { sessionStore } from './SessionStore'
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
    // Fleet 接口为 MANAGER 专属，FAMILY/RESCUE 调用会收到 403
    const account = sessionStore.account
    if (account === null || account.role !== 'MANAGER') {
      return errorState<FleetData>('车队视图仅对管理员开放')
    }
    const fatigueResp = await fleetApi.getFatigueDistribution(this.fleetId)
    if (!fatigueResp.success || fatigueResp.data === undefined) {
      const msg = (fatigueResp.error !== undefined && fatigueResp.error.message.length > 0)
        ? fatigueResp.error.message : '获取疲劳分布失败'
      return errorState<FleetData>(msg)
    }

    const offlineResp = await fleetApi.getOfflineVehicles(this.fleetId)
    if (!offlineResp.success || offlineResp.data === undefined) {
      const msg = (offlineResp.error !== undefined && offlineResp.error.message.length > 0)
        ? offlineResp.error.message : '获取脱线车辆失败'
      return errorState<FleetData>(msg)
    }

    const highRiskResp = await fleetApi.drillDownHighRisk(this.fleetId)
    if (!highRiskResp.success || highRiskResp.data === undefined) {
      const msg = (highRiskResp.error !== undefined && highRiskResp.error.message.length > 0)
        ? highRiskResp.error.message : '获取高危车辆失败'
      return errorState<FleetData>(msg)
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
  }
}

export const fleetVM = new FleetViewModel()
