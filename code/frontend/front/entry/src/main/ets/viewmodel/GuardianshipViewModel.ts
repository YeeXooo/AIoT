/**
 * Guardianship VM — 监护司机列表 + 快捷操作。
 *
 * 注意：GuardianshipApi 依赖的 ApiClient 暂不可用，本 VM 返回 mock。
 */
import { ViewState, successState } from './ViewState'
import type { StatusColor } from './DashboardViewModel'

export interface GuardianDriver {
  id: string
  name: string
  status: StatusColor | 'OFFLINE'
  vehicleId: string
}

export interface GuardianshipData {
  drivers: GuardianDriver[]
}

/** 快捷操作结果（显式 interface，满足 arkts-no-untyped-obj-literals） */
export interface ActionResult {
  ok: boolean
  msg: string
}

export type QuickAction = 'MEDIA_CALL' | 'RESCUE' | 'WINDOW' | 'NOTIFY'

function mockDrivers(): GuardianDriver[] {
  return [
    { id: 'd1', name: '张明', status: 'GREEN', vehicleId: '京A·88562' },
    { id: 'd2', name: '李强', status: 'YELLOW', vehicleId: '京B·33217' },
    { id: 'd3', name: '王磊', status: 'OFFLINE', vehicleId: '京C·77501' },
  ]
}

export class GuardianshipViewModel {
  private _drivers: GuardianDriver[] = mockDrivers()

  get drivers(): GuardianDriver[] {
    return this._drivers
  }

  async loadPermissions(_driverId: string): Promise<string[]> {
    return ['MEDIA_CALL', 'WINDOW_CONTROL', 'MANUAL_RESCUE']
  }

  async requestCall(_driverId: string, _accountId: string): Promise<ActionResult> {
    return { ok: true, msg: '对讲请求已发送' }
  }

  async triggerRescue(_driverId: string, _accountId: string): Promise<ActionResult> {
    return { ok: true, msg: '救援请求已提交' }
  }

  data(): ViewState<GuardianshipData> {
    return successState<GuardianshipData>({ drivers: this._drivers })
  }
}

export const guardianshipVM = new GuardianshipViewModel()
