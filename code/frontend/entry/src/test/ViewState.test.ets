/**
 * ViewState 视图状态机单元测试
 *
 * 覆盖 successState / errorState 工厂函数。
 */
import { describe, it, expect } from '@ohos/hypium'
import { successState, errorState } from '../main/ets/viewmodel/ViewState'

export default function viewStateTest() {
  describe('ViewState', () => {
    // ===================================================================
    // successState
    // ===================================================================
    it('successState_字符串数据', 0, () => {
      const s = successState('ok')
      expect(s.state).assertEqual('success')
      expect(s.data).assertEqual('ok')
      expect(s.errorMsg).assertEqual('')
    })

    it('successState_数字数据', 0, () => {
      const s = successState(42)
      expect(s.state).assertEqual('success')
      expect(s.data).assertEqual(42)
      expect(s.errorMsg).assertEqual('')
    })

    it('successState_对象数据', 0, () => {
      const obj = { 'key': 'val' }
      const s = successState(obj)
      expect(s.state).assertEqual('success')
      if (s.data !== null) {
        const d = s.data as Record<string, Object>
        expect(d['key']).assertEqual('val')
      }
    })

    it('successState_null数据', 0, () => {
      const s = successState(null)
      expect(s.state).assertEqual('success')
      expect(s.data).assertNull()
      expect(s.errorMsg).assertEqual('')
    })

    // ===================================================================
    // errorState
    // ===================================================================
    it('errorState_错误消息', 0, () => {
      const s = errorState('网络连接失败')
      expect(s.state).assertEqual('error')
      expect(s.data).assertNull()
      expect(s.errorMsg).assertEqual('网络连接失败')
    })

    it('errorState_空错误消息', 0, () => {
      const s = errorState('')
      expect(s.state).assertEqual('error')
      expect(s.data).assertNull()
      expect(s.errorMsg).assertEqual('')
    })
  })
}
