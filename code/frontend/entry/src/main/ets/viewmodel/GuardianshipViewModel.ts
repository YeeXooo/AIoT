import { ViewState, successState } from './ViewState'
import type { StatusColor } from './DashboardViewModel'
import { guardianshipApi } from '../api/GuardianshipApi'
import { driverApi } from '../api/DriverApi'
import { sessionStore } from './SessionStore'

export interface GuardianDriver {
  id: string
  name: string
  status: StatusColor | 'OFFLINE'
  vehicleId: string
}

export interface GuardianshipData {
  drivers: GuardianDriver[]
}

export interface ActionResult {
  ok: boolean
  msg: string
}

export type QuickAction = 'MEDIA_CALL' | 'RESCUE' | 'WINDOW' | 'NOTIFY'

export class GuardianshipViewModel {
  private _drivers: GuardianDriver[] = [
    { id: 'd1', name: '张明', status: 'GREEN', vehicleId: '京A·88562' },
    { id: 'd2', name: '李强', status: 'YELLOW', vehicleId: '京B·33217' },
    { id: 'd3', name: '王磊', status: 'OFFLINE', vehicleId: '京C·77501' },
  ]

  get drivers(): GuardianDriver[] {
    return this._drivers
  }

  async loadPermissions(driverId: string): Promise<string[]> {
    try {
      const resp = await guardianshipApi.queryGuardianshipPermissions(driverId)
      if (resp.success && resp.data !== undefined) {
        const perms = resp.data.permissions
        const result: string[] = []
        for (let i = 0; i < perms.length; i++) {
          if (perms[i].granted) {
            result.push(perms[i].permissionType)
          }
        }
        return result
      }
      return []
    } catch (e) {
      return []
    }
  }

  async requestCall(driverId: string, accountId: string): Promise<ActionResult> {
    try {
      const body: Record<string, unknown> = {
        familyAccountId: accountId,
        driverId,
        sessionType: 'AUDIO',
        secondaryAuthToken: '',
      }
      const resp = await guardianshipApi.requestMediaSession(body)
      if (resp.success) {
        return { ok: true, msg: '对讲请求已发送' }
      }
      return { ok: false, msg: resp.error?.message ?? '请求失败' }
    } catch (e) {
      return { ok: false, msg: '请求失败' }
    }
  }

  async triggerRescue(driverId: string, accountId: string): Promise<ActionResult> {
    try {
      const body: Record<string, unknown> = {
        familyAccountId: accountId,
        driverId,
        secondaryAuthToken: '',
      }
      const resp = await guardianshipApi.triggerManualRescue(body)
      if (resp.success) {
        return { ok: true, msg: '救援请求已提交' }
      }
      return { ok: false, msg: resp.error?.message ?? '救援失败' }
    } catch (e) {
      return { ok: false, msg: '救援失败' }
    }
  }

  data(): ViewState<GuardianshipData> {
    return successState<GuardianshipData>({ drivers: this._drivers })
  }

  async addDriver(driverId: string): Promise<ActionResult> {
    const account = sessionStore.account
    const accountId = account !== null ? account.accountId : 'mock_family_001'
    try {
      const resp = await driverApi.bindDriver(accountId, driverId)
      if (resp.success) {
        const newDriver: GuardianDriver = {
          id: driverId,
          name: `司机${driverId}`,
          status: 'GREEN',
          vehicleId: `京D·${driverId.substring(1).padStart(5, '0')}`,
        }
        this._drivers.push(newDriver)
        return { ok: true, msg: '绑定成功' }
      }
      return { ok: false, msg: resp.error?.message ?? '绑定失败' }
    } catch (_e) {
      const newDriver: GuardianDriver = {
        id: driverId,
        name: `司机${driverId}`,
        status: 'GREEN',
        vehicleId: `京D·${driverId.substring(1).padStart(5, '0')}`,
      }
      this._drivers.push(newDriver)
      return { ok: true, msg: '绑定成功(Mock)' }
    }
  }
}

export const guardianshipVM = new GuardianshipViewModel()
