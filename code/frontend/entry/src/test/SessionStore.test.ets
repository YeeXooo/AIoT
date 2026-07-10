/**
 * SessionStore 逻辑单元测试
 *
 * 覆盖不依赖 Preferences 原生能力的核心逻辑：
 *   hasValidSecondaryAuth / setSecondaryAuthToken / clearSecondaryAuth
 */
import { describe, it, expect } from '@ohos/hypium'
import { sessionStore } from '../main/ets/viewmodel/SessionStore'

export default function sessionStoreTest() {
  describe('SessionStore', () => {
    // 每个测试前重置二次验证 token
    beforeEach(() => {
      sessionStore.clearSecondaryAuth()
    })

    // ===================================================================
    // clearSecondaryAuth
    // ===================================================================
    it('clearSecondaryAuth_清除后token为空', 0, () => {
      sessionStore.setSecondaryAuthToken('some_token', '2026-07-10T18:00:00Z')
      sessionStore.clearSecondaryAuth()
      expect(sessionStore.secondaryAuthToken).assertEqual('')
      expect(sessionStore.hasValidSecondaryAuth()).assertFalse()
    })

    // ===================================================================
    // setSecondaryAuthToken
    // ===================================================================
    it('setSecondaryAuthToken_设置有效token', 0, () => {
      const future = '2026-07-10T18:00:00Z'
      sessionStore.setSecondaryAuthToken('my_token', future)
      expect(sessionStore.secondaryAuthToken).assertEqual('my_token')
      expect(sessionStore.hasValidSecondaryAuth()).assertTrue()
    })

    it('setSecondaryAuthToken_过期token', 0, () => {
      const past = '2026-07-09T00:00:00Z' // yesterday
      sessionStore.setSecondaryAuthToken('expired_token', past)
      expect(sessionStore.hasValidSecondaryAuth()).assertFalse()
    })

    // ===================================================================
    // hasValidSecondaryAuth
    // ===================================================================
    it('hasValidSecondaryAuth_空token返回false', 0, () => {
      expect(sessionStore.hasValidSecondaryAuth()).assertFalse()
    })

    it('hasValidSecondaryAuth_非法日期格式走5分钟兜底', 0, () => {
      // 非 ISO 8601 格式，Date.parse 返回 NaN，兜底为当前时间 +5min
      sessionStore.setSecondaryAuthToken('tok', 'not-a-date')
      // 刚设置完，应该在 5 分钟窗口内，所以有效
      expect(sessionStore.hasValidSecondaryAuth()).assertTrue()
    })

    it('hasValidSecondaryAuth_带提前量参数', 0, () => {
      // 设置一个刚好在边界上的过期时间
      const rightNow = new Date()
      const expiresAt = new Date(rightNow.getTime() + 30000) // 30 秒后过期
      sessionStore.setSecondaryAuthToken('tok_boundary', expiresAt.toISOString())
      // 用默认 10s 提前量，应该还来得及
      expect(sessionStore.hasValidSecondaryAuth()).assertTrue()
    })

    it('hasValidSecondaryAuth_大提前量让有效token变无效', 0, () => {
      // 设置一个即将过期但还没过期的 token
      const rightNow = new Date()
      const expiresAt = new Date(rightNow.getTime() + 5000) // 5 秒后过期
      sessionStore.setSecondaryAuthToken('tok_strict', expiresAt.toISOString())
      // 用 10 秒的提前量，5 秒 < 10 秒，视为无效
      expect(sessionStore.hasValidSecondaryAuth(10000)).assertFalse()
    })
  })
}
