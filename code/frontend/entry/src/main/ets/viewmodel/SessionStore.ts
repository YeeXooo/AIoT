/**
 * 全局会话 Store — token / 账户信息 的单例。
 *
 * 注意：暂不依赖 api/ApiClient（其依赖的 fetch/AbortController 在 ArkTS 不可用，
 * 待 ApiClient 改用 @kit.NetworkKit.http 后再接入真实 token 写入）。
 * 当前仅做内存态 + Preferences 持久化，token 字段保留以备后用。
 */
import { preferences } from '@kit.ArkData'
import { common } from '@kit.AbilityKit'
import { apiClient } from '../api/ApiClient'

const PREF_NAME = 'safety_monitor_pref'
const KEY_ACCESS_TOKEN = 'access_token'
const KEY_ACCOUNT_ID = 'account_id'
const KEY_ROLE = 'role'

/** 账户角色字面量（与 model/types 解耦，避免引入未编译模块） */
export type AccountRole = 'FAMILY' | 'MANAGER' | 'RESCUE'

export interface AccountInfo {
  accountId: string
  role: AccountRole
}

export class SessionStore {
  private static _instance: SessionStore | null = null
  private _prefStore: preferences.Preferences | null = null
  private _account: AccountInfo | null = null

  static instance(): SessionStore {
    if (SessionStore._instance === null) {
      SessionStore._instance = new SessionStore()
    }
    return SessionStore._instance
  }

  async init(context: common.Context): Promise<void> {
    try {
      this._prefStore = await preferences.getPreferences(context, PREF_NAME)
      await this.restore()
    } catch (e) {
      // Preferences 不可用时降级为纯内存态
    }
  }

  private async restore(): Promise<void> {
    if (this._prefStore === null) return
    try {
      const accessToken = await this._prefStore.get(KEY_ACCESS_TOKEN, '') as string
      if (accessToken.length > 0) {
        const accountId = await this._prefStore.get(KEY_ACCOUNT_ID, '') as string
        const role = await this._prefStore.get(KEY_ROLE, 'FAMILY') as string
        this._account = { accountId, role: role as AccountRole }
        apiClient.setAccessToken(accessToken)
      }
    } catch (e) {
      // ignore
    }
  }

  get isLoggedIn(): boolean {
    return this._account !== null
  }

  get account(): AccountInfo | null {
    return this._account
  }

  async saveSession(accessToken: string, accountId: string, role: AccountRole): Promise<void> {
    this._account = { accountId, role }
    apiClient.setAccessToken(accessToken)
    if (this._prefStore === null) return
    try {
      await this._prefStore.put(KEY_ACCESS_TOKEN, accessToken)
      await this._prefStore.put(KEY_ACCOUNT_ID, accountId)
      await this._prefStore.put(KEY_ROLE, role)
      await this._prefStore.flush()
    } catch (e) {
      // ignore
    }
  }

  async clear(): Promise<void> {
    this._account = null
    apiClient.setAccessToken(null)
    if (this._prefStore === null) return
    try {
      await this._prefStore.delete(KEY_ACCESS_TOKEN)
      await this._prefStore.delete(KEY_ACCOUNT_ID)
      await this._prefStore.delete(KEY_ROLE)
      await this._prefStore.flush()
    } catch (e) {
      // ignore
    }
  }
}

export const sessionStore = SessionStore.instance()
