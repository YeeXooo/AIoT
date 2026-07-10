/**
 * Fleet 模型 fromJson 构造器单元测试
 *
 * 覆盖 fleet.ts 中核心 DTO 构造器。
 * 由于 fleet.ts 中 DTO 较多，此处选取代表性构造器覆盖。
 */
import { describe, it, expect } from '@ohos/hypium'
import {
  heatmapPointFromJson,
  getFatigueDistributionResponseFromJson,
  offlineVehicleEntryFromJson,
  getOfflineVehiclesResponseFromJson,
  trajectoryPointFromJson,
  queryTrajectoryResponseFromJson,
  latestTripSummaryFromJson,
  highRiskDriverEntryFromJson,
  drillDownHighRiskResponseFromJson,
  timeRangeFromJson,
  subScoresFromJson,
  drivingBehaviorSummaryFromJson,
  riskDistributionFromJson,
  penaltyBreakdownEntryFromJson,
  reportDataFromJson,
  generateReportResponseFromJson,
  subscribePerformanceWarningResponseFromJson,
} from '../main/ets/model/fleet'
import type {
  HeatmapPoint,
  GetFatigueDistributionResponse,
  OfflineVehicleEntry,
  GetOfflineVehiclesResponse,
  TrajectoryPoint,
  QueryTrajectoryResponse,
  LatestTripSummary,
  HighRiskDriverEntry,
  DrillDownHighRiskResponse,
  ReportData,
  GenerateReportResponse,
  SubscribePerformanceWarningResponse,
  DrivingBehaviorSummary,
  RiskDistribution,
  PenaltyBreakdownEntry,
  SubScores,
} from '../main/ets/model/fleet'
import type { DataConsistency } from '../main/ets/model/types'

export default function modelFleetTest() {
  describe('ModelFleet', () => {
    // ===================================================================
    // heatmapPointFromJson
    // ===================================================================
    it('heatmapPointFromJson_完整', 0, () => {
      const raw: Record<string, Object> = { 'latitude': 39.9, 'longitude': 116.4, 'riskIntensity': 5 }
      const pt: HeatmapPoint = heatmapPointFromJson(raw)
      expect(pt.latitude).assertEqual(39.9)
      expect(pt.longitude).assertEqual(116.4)
      expect(pt.riskIntensity).assertEqual(5)
    })

    // ===================================================================
    // getFatigueDistributionResponseFromJson
    // ===================================================================
    it('getFatigueDistributionResponseFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'distribution': {
          'L1_HINT': 10,
          'L2_WARNING': 5,
          'L3_CRITICAL': 2,
        } as Record<string, Object>,
        'heatmapData': [] as Array<Record<string, Object>>,
        'dataFreshness': 'FRESH',
        'generatedAt': '2026-07-10T12:00:00Z',
      }
      const dto: GetFatigueDistributionResponse = getFatigueDistributionResponseFromJson(raw)
      expect(dto.distribution.L1_HINT).assertEqual(10)
      expect(dto.distribution.L2_WARNING).assertEqual(5)
      expect(dto.distribution.L3_CRITICAL).assertEqual(2)
      expect(dto.dataFreshness).assertEqual('FRESH')
      expect(dto.generatedAt).assertEqual('2026-07-10T12:00:00Z')
    })

    it('getFatigueDistributionResponseFromJson_STALE', 0, () => {
      const raw: Record<string, Object> = {
        'distribution': { 'L1_HINT': 0, 'L2_WARNING': 0, 'L3_CRITICAL': 0 } as Record<string, Object>,
        'heatmapData': [] as Array<Record<string, Object>>,
        'dataFreshness': 'STALE',
        'generatedAt': '2026-07-09T00:00:00Z',
      }
      expect(getFatigueDistributionResponseFromJson(raw).dataFreshness).assertEqual('STALE')
    })

    // ===================================================================
    // offlineVehicleEntryFromJson
    // ===================================================================
    it('offlineVehicleEntryFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'vehicleId': 'v_001',
        'licensePlate': '京A·88888',
        'driverId': 'd_001',
        'driverName': '张三',
        'offlineReason': 'COMMUNICATION_LOST',
        'offlineSince': '2026-07-10T08:00:00Z',
        'lastHeartbeat': '2026-07-10T07:59:00Z',
      }
      const entry: OfflineVehicleEntry = offlineVehicleEntryFromJson(raw)
      expect(entry.vehicleId).assertEqual('v_001')
      expect(entry.licensePlate).assertEqual('京A·88888')
      expect(entry.driverName).assertEqual('张三')
      expect(entry.offlineReason).assertEqual('COMMUNICATION_LOST')
    })

    it('offlineVehicleEntryFromJson_SENSOR_FAULT', 0, () => {
      const raw: Record<string, Object> = {
        'vehicleId': 'v_002',
        'licensePlate': '京B·66666',
        'driverId': 'd_002',
        'driverName': '李四',
        'offlineReason': 'SENSOR_FAULT',
        'offlineSince': 't1',
        'lastHeartbeat': 't0',
      }
      expect(offlineVehicleEntryFromJson(raw).offlineReason).assertEqual('SENSOR_FAULT')
    })

    // ===================================================================
    // getOfflineVehiclesResponseFromJson
    // ===================================================================
    it('getOfflineVehiclesResponseFromJson_多条', 0, () => {
      const raw: Record<string, Object> = {
        'offlineVehicles': [
          { 'vehicleId': 'v1', 'licensePlate': 'p1', 'driverId': 'd1', 'driverName': 'n1',
            'offlineReason': 'COMMUNICATION_LOST', 'offlineSince': 't1', 'lastHeartbeat': 't0' } as Record<string, Object>,
          { 'vehicleId': 'v2', 'licensePlate': 'p2', 'driverId': 'd2', 'driverName': 'n2',
            'offlineReason': 'SENSOR_FAULT', 'offlineSince': 't2', 'lastHeartbeat': 't1' } as Record<string, Object>,
        ] as Array<Record<string, Object>>,
      }
      const dto: GetOfflineVehiclesResponse = getOfflineVehiclesResponseFromJson(raw)
      expect(dto.offlineVehicles.length).assertEqual(2)
      expect(dto.offlineVehicles[0].vehicleId).assertEqual('v1')
      expect(dto.offlineVehicles[1].vehicleId).assertEqual('v2')
    })

    it('getOfflineVehiclesResponseFromJson_空', 0, () => {
      const raw: Record<string, Object> = { 'offlineVehicles': [] as Array<Record<string, Object>> }
      expect(getOfflineVehiclesResponseFromJson(raw).offlineVehicles.length).assertEqual(0)
    })

    // ===================================================================
    // trajectoryPointFromJson
    // ===================================================================
    it('trajectoryPointFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'latitude': 39.9,
        'longitude': 116.4,
        'timestamp': '2026-07-10T10:00:00Z',
        'speed': 45,
      }
      const pt: TrajectoryPoint = trajectoryPointFromJson(raw)
      expect(pt.latitude).assertEqual(39.9)
      expect(pt.longitude).assertEqual(116.4)
      expect(pt.speed).assertEqual(45)
    })

    // ===================================================================
    // queryTrajectoryResponseFromJson
    // ===================================================================
    it('queryTrajectoryResponseFromJson_多条', 0, () => {
      const raw: Record<string, Object> = {
        'trajectoryPoints': [
          { 'latitude': 39.9, 'longitude': 116.4, 'timestamp': 't1', 'speed': 0 } as Record<string, Object>,
          { 'latitude': 39.91, 'longitude': 116.41, 'timestamp': 't2', 'speed': 0 } as Record<string, Object>,
        ] as Array<Record<string, Object>>,
        'totalCount': 2,
        'dataConsistency': 'CONSISTENT',
      }
      const dto: QueryTrajectoryResponse = queryTrajectoryResponseFromJson(raw)
      expect(dto.trajectoryPoints.length).assertEqual(2)
      expect(dto.totalCount).assertEqual(2)
      expect(dto.dataConsistency).assertEqual('CONSISTENT')
    })

    // ===================================================================
    // latestTripSummaryFromJson
    // ===================================================================
    it('latestTripSummaryFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'tripId': 'trip_001',
        'startTime': '2026-07-10T08:00:00Z',
        'endTime': '2026-07-10T12:00:00Z',
        'score': 92,
      }
      const dto: LatestTripSummary = latestTripSummaryFromJson(raw)
      expect(dto.tripId).assertEqual('trip_001')
      expect(dto.startTime).assertEqual('2026-07-10T08:00:00Z')
      expect(dto.endTime).assertEqual('2026-07-10T12:00:00Z')
      expect(dto.score).assertEqual(92)
    })

    // ===================================================================
    // highRiskDriverEntryFromJson
    // ===================================================================
    it('highRiskDriverEntryFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'driverId': 'd_001',
        'driverName': '王五',
        'vehicleId': 'v_001',
        'score': 45,
        'primaryRisk': 'FATIGUE',
      }
      const entry: HighRiskDriverEntry = highRiskDriverEntryFromJson(raw)
      expect(entry.driverId).assertEqual('d_001')
      expect(entry.driverName).assertEqual('王五')
      expect(entry.score).assertEqual(45)
      expect(entry.primaryRisk).assertEqual('FATIGUE')
    })

    // ===================================================================
    // drillDownHighRiskResponseFromJson
    // ===================================================================
    it('drillDownHighRiskResponseFromJson_多条', 0, () => {
      const raw: Record<string, Object> = {
        'drivers': [
          { 'driverId': 'd1', 'driverName': 'n1', 'vehicleId': 'v1', 'score': 30, 'primaryRisk': 'DISTRACTION' } as Record<string, Object>,
          { 'driverId': 'd2', 'driverName': 'n2', 'vehicleId': 'v2', 'score': 25, 'primaryRisk': 'FATIGUE' } as Record<string, Object>,
        ] as Array<Record<string, Object>>,
      }
      const dto: DrillDownHighRiskResponse = drillDownHighRiskResponseFromJson(raw)
      expect(dto.drivers.length).assertEqual(2)
      expect(dto.drivers[0].driverId).assertEqual('d1')
    })

    it('drillDownHighRiskResponseFromJson_空', 0, () => {
      const raw: Record<string, Object> = { 'drivers': [] as Array<Record<string, Object>> }
      expect(drillDownHighRiskResponseFromJson(raw).drivers.length).assertEqual(0)
    })

    // ===================================================================
    // timeRangeFromJson
    // ===================================================================
    it('timeRangeFromJson_完整', 0, () => {
      const raw: Record<string, Object> = { 'start': '2026-07-01T00:00:00Z', 'end': '2026-07-31T23:59:59Z' }
      const tr = timeRangeFromJson(raw)
      expect(tr.start).assertEqual('2026-07-01T00:00:00Z')
      expect(tr.end).assertEqual('2026-07-31T23:59:59Z')
    })

    // ===================================================================
    // subScoresFromJson
    // ===================================================================
    it('subScoresFromJson_完整', 0, () => {
      const raw: Record<string, Object> = { 'fatigueScore': 85, 'distractionScore': 90, 'abnormalDrivingScore': 95 }
      const ss: SubScores = subScoresFromJson(raw)
      expect(ss.fatigueScore).assertEqual(85)
      expect(ss.distractionScore).assertEqual(90)
      expect(ss.abnormalDrivingScore).assertEqual(95)
    })

    // ===================================================================
    // drivingBehaviorSummaryFromJson
    // ===================================================================
    it('drivingBehaviorSummaryFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'overallScore': 88,
        'subScores': {
          'fatigueScore': 85,
          'distractionScore': 90,
          'abnormalDrivingScore': 95,
        } as Record<string, Object>,
        'trendVsLastPeriod': 2,
      }
      const dbs: DrivingBehaviorSummary = drivingBehaviorSummaryFromJson(raw)
      expect(dbs.overallScore).assertEqual(88)
      expect(dbs.subScores.fatigueScore).assertEqual(85)
      expect(dbs.subScores.distractionScore).assertEqual(90)
      expect(dbs.subScores.abnormalDrivingScore).assertEqual(95)
      expect(dbs.trendVsLastPeriod).assertEqual(2)
    })

    // ===================================================================
    // riskDistributionFromJson
    // ===================================================================
    it('riskDistributionFromJson_完整', 0, () => {
      const raw: Record<string, Object> = { 'FATIGUE': 50, 'DISTRACTION': 20, 'ROAD_RAGE': 5 }
      const rd: RiskDistribution = riskDistributionFromJson(raw)
      expect(rd.FATIGUE).assertEqual(50)
      expect(rd.DISTRACTION).assertEqual(20)
      expect(rd.ROAD_RAGE).assertEqual(5)
    })

    // ===================================================================
    // penaltyBreakdownEntryFromJson
    // ===================================================================
    it('penaltyBreakdownEntryFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'category': 'speeding',
        'penaltyScore': 10,
        'topViolations': ['超速10%', '超速20%'] as Array<Record<string, Object>>,
      }
      const pbe: PenaltyBreakdownEntry = penaltyBreakdownEntryFromJson(raw)
      expect(pbe.category).assertEqual('speeding')
      expect(pbe.penaltyScore).assertEqual(10)
      expect(pbe.topViolations.length).assertEqual(2)
    })

    // ===================================================================
    // reportDataFromJson
    // ===================================================================
    it('reportDataFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'reportId': 'rpt_001',
        'driverId': 'd_001',
        'reportType': 'WEEKLY',
        'generatedAt': '2026-07-10T12:00:00Z',
        'timeRange': { 'start': 't1', 'end': 't2' } as Record<string, Object>,
        'drivingBehaviorSummary': {
          'overallScore': 88,
          'subScores': { 'fatigueScore': 85, 'distractionScore': 90, 'abnormalDrivingScore': 95 } as Record<string, Object>,
          'trendVsLastPeriod': 2,
        } as Record<string, Object>,
        'riskDistribution': { 'FATIGUE': 50, 'DISTRACTION': 20, 'ROAD_RAGE': 5 } as Record<string, Object>,
        'penaltyBreakdown': [
          { 'category': 'speeding', 'penaltyScore': 10, 'topViolations': [] as Array<Record<string, Object>> } as Record<string, Object>,
        ] as Array<Record<string, Object>>,
        'totalMileage': 15000,
        'totalDrivingTime': 'PT8H30M',
      }
      const dto: ReportData = reportDataFromJson(raw)
      expect(dto.reportId).assertEqual('rpt_001')
      expect(dto.reportType).assertEqual('WEEKLY')
      expect(dto.driverId).assertEqual('d_001')
      expect(dto.totalMileage).assertEqual(15000)
      expect(dto.totalDrivingTime).assertEqual('PT8H30M')
      expect(dto.drivingBehaviorSummary.overallScore).assertEqual(88)
      expect(dto.riskDistribution.FATIGUE).assertEqual(50)
      expect(dto.penaltyBreakdown.length).assertEqual(1)
    })

    // ===================================================================
    // generateReportResponseFromJson
    // ===================================================================
    it('generateReportResponseFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'reportId': 'rpt_001',
        'reportData': {
          'reportId': 'rpt_001',
          'driverId': 'd_001',
          'reportType': 'WEEKLY',
          'generatedAt': '2026-07-10T12:00:00Z',
          'timeRange': { 'start': 't1', 'end': 't2' } as Record<string, Object>,
          'drivingBehaviorSummary': {
            'overallScore': 88,
            'subScores': { 'fatigueScore': 85, 'distractionScore': 90, 'abnormalDrivingScore': 95 } as Record<string, Object>,
            'trendVsLastPeriod': 2,
          } as Record<string, Object>,
          'riskDistribution': { 'FATIGUE': 50, 'DISTRACTION': 20, 'ROAD_RAGE': 5 } as Record<string, Object>,
          'penaltyBreakdown': [] as Array<Record<string, Object>>,
          'totalMileage': 15000,
          'totalDrivingTime': 'PT8H30M',
        } as Record<string, Object>,
        'downloadUrl': '/api/v1/fleet/reports/rpt_001/download',
        'isEmpty': false,
      }
      const dto: GenerateReportResponse = generateReportResponseFromJson(raw)
      expect(dto.reportId).assertEqual('rpt_001')
      expect(dto.downloadUrl).assertEqual('/api/v1/fleet/reports/rpt_001/download')
      expect(dto.isEmpty).assertFalse()
      expect(dto.reportData.reportId).assertEqual('rpt_001')
    })

    // ===================================================================
    // subscribePerformanceWarningResponseFromJson
    // ===================================================================
    it('subscribePerformanceWarningResponseFromJson_完整', 0, () => {
      const raw: Record<string, Object> = {
        'subscriptionId': 'sub_001',
      }
      const dto: SubscribePerformanceWarningResponse = subscribePerformanceWarningResponseFromJson(raw)
      expect(dto.subscriptionId).assertEqual('sub_001')
    })
  })
}
