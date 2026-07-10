/**
 * StatusMap 纯函数单元测试
 *
 * 覆盖不依赖 $r() 的纯函数：
 *   alertTypeLabel / offlineReasonLabel / formatTime
 */
import { describe, it, expect } from '@ohos/hypium'
import { alertTypeLabel, offlineReasonLabel, formatTime } from '../main/ets/common/StatusMap.ets'

export default function statusMapTest() {
  describe('StatusMap', () => {
    // ===================================================================
    // alertTypeLabel
    // ===================================================================
    it('alertTypeLabel_FATIGUE', 0, () => {
      expect(alertTypeLabel('FATIGUE')).assertEqual('疲劳驾驶')
    })

    it('alertTypeLabel_DISTRACTION', 0, () => {
      expect(alertTypeLabel('DISTRACTION')).assertEqual('分心驾驶')
    })

    it('alertTypeLabel_ROAD_RAGE', 0, () => {
      expect(alertTypeLabel('ROAD_RAGE')).assertEqual('路怒行为')
    })

    it('alertTypeLabel_LIFE_DETECTION', 0, () => {
      expect(alertTypeLabel('LIFE_DETECTION')).assertEqual('生命体征')
    })

    it('alertTypeLabel_COLLISION_DISABILITY', 0, () => {
      expect(alertTypeLabel('COLLISION_DISABILITY')).assertEqual('碰撞失能')
    })

    it('alertTypeLabel_PERFORMANCE_WARNING', 0, () => {
      expect(alertTypeLabel('PERFORMANCE_WARNING')).assertEqual('绩效预警')
    })

    it('alertTypeLabel_未知类型返回默认值', 0, () => {
      expect(alertTypeLabel('UNKNOWN_TYPE' as any)).assertEqual('未知告警')
    })

    // ===================================================================
    // offlineReasonLabel
    // ===================================================================
    it('offlineReasonLabel_SENSOR_FAULT', 0, () => {
      expect(offlineReasonLabel('SENSOR_FAULT')).assertEqual('传感器故障')
    })

    it('offlineReasonLabel_COMMUNICATION_LOST', 0, () => {
      expect(offlineReasonLabel('COMMUNICATION_LOST')).assertEqual('通信中断')
    })

    // ===================================================================
    // formatTime
    // ===================================================================
    it('formatTime_完整ISO', 0, () => {
      expect(formatTime('2026-07-10T14:30:00Z')).assertEqual('14:30')
    })

    it('formatTime_带毫秒ISO', 0, () => {
      expect(formatTime('2026-07-10T08:05:30.123Z')).assertEqual('08:05')
    })

    it('formatTime_短字符串', 0, () => {
      expect(formatTime('short')).assertEqual('short')
    })

    it('formatTime_空字符串', 0, () => {
      expect(formatTime('')).assertEqual('')
    })

    it('formatTime_刚好16字符', 0, () => {
      // 16 字符时 length >= 16 为 true
      expect(formatTime('0123456789ABCDEF')).assertEqual('0123456789ABCDEF')
    })

    it('formatTime_15字符', 0, () => {
      // 15 字符时 length >= 16 为 false，返回原值
      expect(formatTime('123456789012345')).assertEqual('123456789012345')
    })

    it('formatTime_17字符可以截取', 0, () => {
      // 17 字符时 substring(11, 16) 截取
      expect(formatTime('2026-07-10T12:34')).assertEqual('12:34')
    })
  })
}
