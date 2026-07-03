import { preferences } from '@kit.ArkData'
import { common } from '@kit.AbilityKit'
import { ConfigurationConstant } from '@kit.AbilityKit'

const PREF_NAME = 'safety_monitor_pref'
const KEY_IS_DARK = 'is_dark_mode'

export class ThemeStore {
  private static _instance: ThemeStore | null = null
  private _prefStore: preferences.Preferences | null = null
  private _isDark: boolean = true

  static instance(): ThemeStore {
    if (ThemeStore._instance === null) {
      ThemeStore._instance = new ThemeStore()
    }
    return ThemeStore._instance
  }

  async init(context: common.Context): Promise<void> {
    try {
      this._prefStore = await preferences.getPreferences(context, PREF_NAME)
      const val = await this._prefStore.get(KEY_IS_DARK, true) as boolean
      this._isDark = val
      AppStorage.setOrCreate('isDarkMode', val)
    } catch (_e) {
      this._isDark = true
      AppStorage.setOrCreate('isDarkMode', true)
    }
  }

  get isDarkMode(): boolean {
    return this._isDark
  }

  async toggle(isDark: boolean): Promise<void> {
    this._isDark = isDark
    AppStorage.setOrCreate('isDarkMode', isDark)
    if (this._prefStore !== null) {
      try {
        await this._prefStore.put(KEY_IS_DARK, isDark)
        await this._prefStore.flush()
      } catch (_e) {
      }
    }
  }
}

export const themeStore = ThemeStore.instance()
