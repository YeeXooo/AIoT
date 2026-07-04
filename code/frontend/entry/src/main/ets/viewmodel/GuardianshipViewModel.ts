import { ViewState, successState, errorState } from './ViewState'
import type { StatusColor } from './DashboardViewModel'
import { guardianshipApi } from '../api/GuardianshipApi'
import { authApi } from '../api/AuthApi'
import { driverApi } from '../api/DriverApi'
import { sessionStore } from './SessionStore'
import { getStr, getOptStr } from '../common/JsonParser'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0503
const TAG = 'GuardianshipVM'

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
  private _drivers: GuardianDriver[] = []

  get drivers(): GuardianDriver[] {
    return this._drivers
  }

  async loadDrivers(): Promise<ViewState<GuardianshipData>> {
    const account = sessionStore.account
    if (account === null) {
      this._drivers = []
      return errorState<GuardianshipData>('未登录，无法获取绑定驾驶员')
    }
    try {
      const resp = await guardianshipApi.queryBoundDrivers(account.accountId)
      if (!resp.success || resp.data === undefined) {
        const msg = (resp.error !== undefined && resp.error.message.length > 0)
          ? resp.error.message : '获取监护驾驶员列表失败'
        this._drivers = []
        return errorState<GuardianshipData>(msg)
      }
      // /guardianship/list 响应：可能是 { items: [...] } / { drivers: [...] } / 直接 [...]。
      // getArray 对缺失 key 返回 undefined（不会抛），这里手动兜底成空数组，避免 .length 抛 TypeError。
      const raw = resp.data
      const itemsVal = raw['items']
      const driversVal = raw['drivers']
      let rawArr: Array<Record<string, Object>> = []
      if (itemsVal instanceof Array) {
        rawArr = itemsVal as Array<Record<string, Object>>
      } else if (driversVal instanceof Array) {
        rawArr = driversVal as Array<Record<string, Object>>
      } else if (raw instanceof Array) {
        rawArr = raw as Array<Record<string, Object>>
      } else {
        hilog.warn(DOMAIN, TAG, 'loadDrivers: 响应未含 items/drivers 数组，顶层 keys=%{public}s', Object.keys(raw).join(','))
      }

      const list: GuardianDriver[] = []
      for (let i = 0; i < rawArr.length; i++) {
        const item = rawArr[i]
        const id = getStr(item, 'driverId')
        if (id.length === 0) {
          hilog.warn(DOMAIN, TAG, 'loadDrivers: item[%{public}d] driverId empty, keys=%{public}s', i, Object.keys(item).join(','))
          continue
        }
        // 尝试多个常见字段名；首次解析时打印 item keys + 取到的 name，便于按后端真实字段校准
        const driverName = getOptStr(item, 'driverName')
        const nameField = getOptStr(item, 'name')
        const driverObjVal = item['driver']
        let driverObjName: string | undefined
        if (driverObjVal !== undefined && driverObjVal !== null && driverObjVal instanceof Object) {
          driverObjName = getOptStr(driverObjVal as Record<string, Object>, 'name')
            ?? getOptStr(driverObjVal as Record<string, Object>, 'driverName')
        }
        const name = driverName ?? nameField ?? driverObjName ?? ''
        // fallback：用 id 前缀生成短名（如 d001），避免显示完整 UUID 造成"乱码"感
        const shortId = id.length > 4 ? id.substring(0, 4) : id
        const displayName = name.length > 0 ? name : `司机${shortId}`
        if (i === 0) {
          hilog.info(DOMAIN, TAG, 'loadDrivers: item[0] keys=%{public}s, driverName=%{public}s, name=%{public}s, driverObjName=%{public}s, final=%{public}s',
            Object.keys(item).join(','), String(driverName ?? ''), String(nameField ?? ''), String(driverObjName ?? ''), displayName)
        }
        const vehicleId = getOptStr(item, 'vehicleId') ?? ''
        const statusRaw = getOptStr(item, 'status') ?? 'GREEN'
        list.push({
          id,
          name: displayName,
          status: statusRaw as StatusColor | 'OFFLINE',
          vehicleId,
        })
      }
      this._drivers = list

      // 同步第一个绑定驾驶员到 AppStorage，供 DashboardTab 拼接 /drivers/{driverId}/risk-status
      if (list.length > 0) {
        const first = list[0]
        AppStorage.setOrCreate('currentDriverId', first.id)
        AppStorage.setOrCreate('currentDriverName', first.name)
        AppStorage.setOrCreate('currentDriverVehicleId', first.vehicleId)
        hilog.info(DOMAIN, TAG, 'loadDrivers: set currentDriverId=%{public}s, count=%{public}d', first.id, list.length)
      } else {
        hilog.warn(DOMAIN, TAG, 'loadDrivers: 绑定驾驶员列表为空')
      }

      return successState<GuardianshipData>({ drivers: list })
    } catch (e) {
      const errMsg = String(e)
      hilog.error(DOMAIN, TAG, 'loadDrivers exception: %{public}s', errMsg)
      this._drivers = []
      return errorState<GuardianshipData>('加载驾驶员列表异常: ' + errMsg)
    }
  }

  /**
   * 确保二次验证 token 有效。高敏操作（对讲/救援/车窗控制）前调用。
   * token 仍有效则直接返回；否则自动调 /auth/secondary-verify 签发新 token。
   * @returns 有效 token；失败时返回空串
   */
  async ensureSecondaryAuth(): Promise<string> {
    if (sessionStore.hasValidSecondaryAuth()) {
      return sessionStore.secondaryAuthToken
    }
    const account = sessionStore.account
    if (account === null) return ''
    try {
      const req: Record<string, Object> = {
        'accountId': account.accountId,
        'method': 'OTP',
        'otp': '000000',
      }
      const resp = await authApi.secondaryVerify(req)
      if (resp.success && resp.data !== undefined) {
        sessionStore.setSecondaryAuthToken(resp.data.secondaryAuthToken, resp.data.expiresAt)
        hilog.info(DOMAIN, TAG, 'ensureSecondaryAuth: token 获取成功, expiresAt=%{public}s', resp.data.expiresAt)
        return resp.data.secondaryAuthToken
      }
      hilog.error(DOMAIN, TAG, 'ensureSecondaryAuth: 失败 status=%{public}d', resp.status)
      return ''
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'ensureSecondaryAuth exception: %{public}s', String(e))
      return ''
    }
  }

  /**
   * 查询监护权限。
   * @returns 成功时返回权限数组（可能为空，表示无任何权限）；
   *          查询失败（403/网络错误/异常）时返回 undefined，调用方可保留默认权限或降级处理，
   *          避免后端鉴权异常清空前端按钮可用态。
   */
  async loadPermissions(driverId: string): Promise<string[] | undefined> {
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
      // 查询失败（403 等）——返回 undefined 而非空数组，让调用方区分"失败"与"成功但无权限"
      return undefined
    } catch (e) {
      return undefined
    }
  }

  async requestCall(driverId: string, accountId: string): Promise<ActionResult> {
    try {
      const body: Record<string, Object> = {
        'familyAccountId': accountId,
        'driverId': driverId,
        'sessionType': 'AUDIO',
        'secondaryAuthToken': '',
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
      const body: Record<string, Object> = {
        'familyAccountId': accountId,
        'driverId': driverId,
        'secondaryAuthToken': '',
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
    const accountId = account !== null ? account.accountId : ''
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
  }
}

export const guardianshipVM = new GuardianshipViewModel()
