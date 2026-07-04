/**
 * Guardianship VM — 监护司机列表 + 快捷操作。
 * 司机列表从后端 /api/v1/guardianship/drivers 加载，不再硬编码 mock。
 */
import { ViewState, successState, errorState } from './ViewState'
import type { StatusColor } from './DashboardViewModel'
import { guardianshipApi } from '../api/GuardianshipApi'
import { getArray, getStr } from '../common/JsonParser'

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

export class GuardianshipViewModel {
  private _drivers: GuardianDriver[] = []

  get drivers(): GuardianDriver[] {
    return this._drivers
  }

  async loadDrivers(): Promise<ViewState<GuardianshipData>> {
    const resp = await guardianshipApi.queryBoundDrivers()
    if (!resp.success || resp.data === undefined) {
      const msg = (resp.error !== undefined && resp.error.message.length > 0)
        ? resp.error.message : '获取监护驾驶员列表失败'
      this._drivers = []
      return errorState<GuardianshipData>(msg)
    }
    const list: GuardianDriver[] = []
    const items = getArray(resp.data, 'drivers')
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      list.push({
        id: getStr(item, 'driverId'),
        name: getStr(item, 'driverName'),
        status: getStr(item, 'status') as StatusColor | 'OFFLINE',
        vehicleId: getStr(item, 'vehicleId'),
      })
    }
    this._drivers = list
    return successState<GuardianshipData>({ drivers: list })
  }

  async loadPermissions(driverId: string): Promise<string[]> {
    const resp = await guardianshipApi.queryGuardianshipPermissions(driverId)
    if (resp.success && resp.data !== undefined) {
      const perms = getArray(resp.data, 'permissions')
      const result: string[] = []
      for (let i = 0; i < perms.length; i++) {
        if (perms[i]['granted'] === true) {
          result.push(getStr(perms[i], 'permissionType'))
        }
      }
      return result
    }
    return []
  }

  async requestCall(driverId: string, accountId: string): Promise<ActionResult> {
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
  }

  async triggerRescue(driverId: string, accountId: string): Promise<ActionResult> {
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
  }

  data(): ViewState<GuardianshipData> {
    return successState<GuardianshipData>({ drivers: this._drivers })
  }
}

export const guardianshipVM = new GuardianshipViewModel()
