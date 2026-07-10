/**
 * Driver 模型 fromJson 构造器单元测试
 *
 * 覆盖 driver.ts 中所有 DTO 构造器：
 *   activeAlertEntryFromJson / getDriverRiskStatusResponseFromJson
 *   alertSummaryFromJson / queryAlertHistoryResponseFromJson
 *   driverRealtimeStatusFromJson
 */
import { describe, it, expect } from '@ohos/hypium'
import {
  activeAlertEntryFromJson,
  getDriverRiskStatusResponseFromJson,
  alertSummaryFromJson,
  queryAlertHistoryResponseFromJson,
  driverRealtimeStatusFromJson,
} from '../main/ets/model/driver'
import type {
  ActiveAlertEntry,
  GetDriverRiskStatusResponse,
  AlertSummary,
  QueryAlertHistoryResponse,
  DriverRealtimeStatus,
} from '../main/ets/model/driver'

export default function modelDriverTest() {
  describe('ModelDriver', () => {
    // ===================================================================
    // activeAlertEntryFromJson
    // ===================================================================
    it('activeAlertEntryFromJson_完整数据', 0, () => {
      const raw: Record<string, Object> = { 'alertType': 'FATIGUE', 'riskLevel': 'L2_WARNING' }
      const entry: ActiveAlertEntry = activeAlertEntryFromJson(raw)
      expect(entry.alertType).assertEqual('FATIGUE')
      expect(entry.riskLevel).assertEqual('L2_WARNING')
    })

    it('activeAlertEntryFromJson_所有告警类型', 0, () => {
      const types = ['FATIGUE', 'DISTRACTION', 'ROAD_RAGE', 'LIFE_DETECTION', 'COLLISION_DISABILITY', 'PERFORMANCE_WARNING']
      for (let i = 0; i < types.length; i++) {
        const raw: Record<string, Object> = { 'alertType': types[i], 'riskLevel': 'L1_HINT' }
        expect(activeAlertEntryFromJson(raw).alertType).assertEqual(types[i])
      }
    })

    it('activeAlertEntryFromJson_所有风险等级', 0, () => {
      const levels = ['L1_HINT', 'L2_WARNING', 'L3_CRITICAL']
      for (let i = 0; i < levels.length; i++) {
        const raw: Record<string, Object> = { 'alertType': 'FATIGUE', 'riskLevel': levels[i] }
        expect(activeAlertEntryFromJson(raw).riskLevel).assertEqual(levels[i])
      }
    })

    // ===================================================================
    // getDriverRiskStatusResponseFromJson
    // ===================================================================
    it('getDriverRiskStatusResponseFromJson_有活跃告警', 0, () => {
      const raw: Record<string, Object> = {
        'hasActiveTrip': true,
        'activeAlerts': [
          { 'alertType': 'FATIGUE', 'riskLevel': 'L3_CRITICAL' } as Record<string, Object>,
          { 'alertType': 'DISTRACTION', 'riskLevel': 'L2_WARNING' } as Record<string, Object>,
        ] as Array<Record<string, Object>>,
        'derivedStatusColor': 'RED',
      }
      const resp: GetDriverRiskStatusResponse = getDriverRiskStatusResponseFromJson(raw)
      expect(resp.hasActiveTrip).assertTrue()
      expect(resp.activeAlerts.length).assertEqual(2)
      expect(resp.activeAlerts[0].alertType).assertEqual('FATIGUE')
      expect(resp.activeAlerts[0].riskLevel).assertEqual('L3_CRITICAL')
      expect(resp.activeAlerts[1].alertType).assertEqual('DISTRACTION')
      expect(resp.derivedStatusColor).assertEqual('RED')
    })

    it('getDriverRiskStatusResponseFromJson_无活跃告警', 0, () => {
      const raw: Record<string, Object> = {
        'hasActiveTrip': false,
        'activeAlerts': [] as Array<Record<string, Object>>,
        'derivedStatusColor': 'GREEN',
      }
      const resp = getDriverRiskStatusResponseFromJson(raw)
      expect(resp.hasActiveTrip).assertFalse()
      expect(resp.activeAlerts.length).assertEqual(0)
      expect(resp.derivedStatusColor).assertEqual('GREEN')
    })

    // ===================================================================
    // alertSummaryFromJson
    // ===================================================================
    it('alertSummaryFromJson_有GPS已解决', 0, () => {
      const raw: Record<string, Object> = {
        'alertId': 'alert_001',
        'alertType': 'COLLISION_DISABILITY',
        'riskLevel': 'L3_CRITICAL',
        'occurredAt': '2026-07-10T10:00:00Z',
        'resolvedAt': '2026-07-10T10:05:00Z',
        'tripId': 'trip_001',
        'gpsLocation': { 'latitude': 39.9042, 'longitude': 116.4074 } as Record<string, Object>,
      }
      const s: AlertSummary = alertSummaryFromJson(raw)
      expect(s.alertId).assertEqual('alert_001')
      expect(s.alertType).assertEqual('COLLISION_DISABILITY')
      expect(s.riskLevel).assertEqual('L3_CRITICAL')
      expect(s.occurredAt).assertEqual('2026-07-10T10:00:00Z')
      expect(s.resolvedAt).assertEqual('2026-07-10T10:05:00Z')
      expect(s.tripId).assertEqual('trip_001')
      expect(s.gpsLocation).assertEqual(s.gpsLocation)
      if (s.gpsLocation !== undefined) {
        expect(s.gpsLocation.latitude).assertEqual(39.9042)
        expect(s.gpsLocation.longitude).assertEqual(116.4074)
      }
    })

    it('alertSummaryFromJson_无GPS未解决', 0, () => {
      const raw: Record<string, Object> = {
        'alertId': 'alert_002',
        'alertType': 'FATIGUE',
        'riskLevel': 'L1_HINT',
        'occurredAt': '2026-07-10T11:00:00Z',
        'tripId': 'trip_002',
        // 无 resolvedAt，无 gpsLocation
      }
      const s = alertSummaryFromJson(raw)
      expect(s.resolvedAt).assertUndefined()
      expect(s.gpsLocation).assertUndefined()
    })

    // ===================================================================
    // queryAlertHistoryResponseFromJson
    // ===================================================================
    it('queryAlertHistoryResponseFromJson_多条记录', 0, () => {
      const raw: Record<string, Object> = {
        'alerts': [
          { 'alertId': 'a1', 'alertType': 'FATIGUE', 'riskLevel': 'L1_HINT', 'occurredAt': 't1', 'tripId': 'tp1' } as Record<string, Object>,
          { 'alertId': 'a2', 'alertType': 'DISTRACTION', 'riskLevel': 'L2_WARNING', 'occurredAt': 't2', 'tripId': 'tp2' } as Record<string, Object>,
        ] as Array<Record<string, Object>>,
        'totalCount': 2,
      }
      const resp: QueryAlertHistoryResponse = queryAlertHistoryResponseFromJson(raw)
      expect(resp.alerts.length).assertEqual(2)
      expect(resp.totalCount).assertEqual(2)
    })

    it('queryAlertHistoryResponseFromJson_空', 0, () => {
      const raw: Record<string, Object> = {
        'alerts': [] as Array<Record<string, Object>>,
        'totalCount': 0,
      }
      const resp = queryAlertHistoryResponseFromJson(raw)
      expect(resp.alerts.length).assertEqual(0)
      expect(resp.totalCount).assertEqual(0)
    })

    // ===================================================================
    // driverRealtimeStatusFromJson
    // ===================================================================
    it('driverRealtimeStatusFromJson_完整数据含生理+感知', 0, () => {
      const raw: Record<string, Object> = {
        'physiologicalSummary': {
          'heartRate': 72,
          'spo2': 98,
          'emotionIndex': 0.85,
        } as Record<string, Object>,
        'perception': {
          'faceConfidence': 0.95,
          'fatigueLevel': 'low',
          'fatigueDesc': '正常',
          'distractionLevel': 'none',
          'distractionDesc': '专注',
          'handsOnWheel': true,
          'phoneDetected': false,
          'smokingDetected': false,
          'emotion': 'neutral',
        } as Record<string, Object>,
        'speed': 60,
      }
      const dto: DriverRealtimeStatus = driverRealtimeStatusFromJson(raw)
      expect(dto.heartRate).assertEqual(72)
      expect(dto.spo2).assertEqual(98)
      expect(dto.emotionIndex).assertEqual(0.85)
      expect(dto.speed).assertEqual(60)
      expect(dto.perception).assertEqual(dto.perception)
      if (dto.perception !== null) {
        expect(dto.perception.faceConfidence).assertEqual(0.95)
        expect(dto.perception.fatigueLevel).assertEqual('low')
        expect(dto.perception.handsOnWheel).assertTrue()
        expect(dto.perception.phoneDetected).assertFalse()
      }
    })

    it('driverRealtimeStatusFromJson_无生理无感知', 0, () => {
      const raw: Record<string, Object> = { 'speed': 0 }
      const dto = driverRealtimeStatusFromJson(raw)
      expect(dto.heartRate).assertEqual(0)
      expect(dto.spo2).assertEqual(0)
      expect(dto.emotionIndex).assertEqual(0)
      expect(dto.speed).assertEqual(0)
      expect(dto.perception).assertNull()
    })

    it('driverRealtimeStatusFromJson_仅生理无感知', 0, () => {
      const raw: Record<string, Object> = {
        'physiologicalSummary': {
          'heartRate': 80,
          'spo2': 97,
          'emotionIndex': 0.5,
        } as Record<string, Object>,
      }
      const dto = driverRealtimeStatusFromJson(raw)
      expect(dto.heartRate).assertEqual(80)
      expect(dto.spo2).assertEqual(97)
      expect(dto.emotionIndex).assertEqual(0.5)
      expect(dto.perception).assertNull()
    })
  })
}
