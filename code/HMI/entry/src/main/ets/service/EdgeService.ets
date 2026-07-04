import { InterventionInstruction } from '../model/InterventionInstruction'
import { InterventionType } from '../model/InterventionType'
import { OverrideSignal, OverrideType } from '../model/OverrideSignal'
import { RiskLevel } from '../model/RiskLevel'
import { QueryInterventionStatusResponse, ReportOverrideResponse, GetDriverRiskStatusResponse } from '../model/ServiceResponse'
import { SensorStatus } from '../model/SensorStatus'
import { http } from '@kit.NetworkKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0001
const TAG = 'EdgeService'

export class EdgeService {
  private baseUrl: string = 'http://127.0.0.1:8080/api/v1'
  private tripId: string = 't006-67fa-89bc-01de-23456789fabc'
  private driverId: string = 'd001-d2e3-4abc-9f01-123456789abc'
  private useMock: boolean = false

  setTripId(tripId: string): void {
    this.tripId = tripId
  }

  setDriverId(driverId: string): void {
    this.driverId = driverId
  }

  setUseMock(useMock: boolean): void {
    this.useMock = useMock
  }

  async queryInterventionStatus(): Promise<QueryInterventionStatusResponse> {
    if (this.useMock) {
      return this.mockQueryInterventionStatus()
    }
    try {
      const request = http.createHttp()
      const response = await request.request(
        this.baseUrl + '/trips/' + this.tripId + '/interventions/active',
        { method: http.RequestMethod.GET }
      )
      if (response.responseCode === 200) {
        const result = response.result as string
        hilog.info(DOMAIN, TAG, 'queryInterventionStatus response: ' + result)
        return this.parseInterventionResponse(result)
      }
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'queryInterventionStatus failed: ' + JSON.stringify(err))
    }
    return new QueryInterventionStatusResponse(this.tripId, RiskLevel.NONE, [])
  }

  async reportOverride(signal: OverrideSignal): Promise<ReportOverrideResponse> {
    const mappedType = this.mapOverrideType(signal.type)
    if (this.useMock) {
      hilog.info(DOMAIN, TAG, 'reportOverride: ' + signal.type)
      return new ReportOverrideResponse(true, '覆盖信号已接受')
    }
    try {
      const request = http.createHttp()
      const body = JSON.stringify({
        type: mappedType,
        timestamp: signal.timestamp
      })
      const response = await request.request(
        this.baseUrl + '/trips/' + this.tripId + '/override',
        {
          method: http.RequestMethod.POST,
          header: { 'Content-Type': 'application/json' },
          extraData: body
        }
      )
      if (response.responseCode === 200) {
        return new ReportOverrideResponse(true, '覆盖信号已接受')
      }
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'reportOverride failed: ' + JSON.stringify(err))
    }
    return new ReportOverrideResponse(false, '覆盖信号上报失败')
  }

  async getDriverRiskStatus(): Promise<GetDriverRiskStatusResponse> {
    if (this.useMock) {
      return new GetDriverRiskStatusResponse(this.driverId, RiskLevel.NONE, '正常驾驶')
    }
    try {
      const request = http.createHttp()
      const response = await request.request(
        this.baseUrl + '/drivers/' + this.driverId + '/risk-status',
        { method: http.RequestMethod.GET }
      )
      if (response.responseCode === 200) {
        const result = response.result as string
        hilog.info(DOMAIN, TAG, 'getDriverRiskStatus response: ' + result)
        return this.parseRiskStatusResponse(result)
      }
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'getDriverRiskStatus failed: ' + JSON.stringify(err))
    }
    return new GetDriverRiskStatusResponse(this.driverId, RiskLevel.NONE, '未知')
  }

  async querySensorStatus(): Promise<SensorStatus> {
    if (this.useMock) {
      return SensorStatus.createDefault()
    }
    return SensorStatus.createDefault()
  }

  reportCameraOcclusion(occluded: boolean): void {
    hilog.info(DOMAIN, TAG, 'Camera occlusion reported: ' + occluded.toString())
  }

  private mockQueryInterventionStatus(): QueryInterventionStatusResponse {
    return new QueryInterventionStatusResponse(this.tripId, RiskLevel.NONE, [])
  }

  private parseInterventionResponse(json: string): QueryInterventionStatusResponse {
    try {
      const data = JSON.parse(json) as Record<string, Object>
      const tripId = data['tripId'] as string ?? this.tripId
      const riskLevelStr = data['riskLevel'] as string ?? 'NONE'
      let riskLevel = RiskLevel.NONE
      if (riskLevelStr === 'L1') {
        riskLevel = RiskLevel.L1
      } else if (riskLevelStr === 'L2') {
        riskLevel = RiskLevel.L2
      } else if (riskLevelStr === 'L3') {
        riskLevel = RiskLevel.L3
      }
      const rawInstructions = data['activeInstructions'] as Array<Record<string, Object>> ?? []
      const instructions: InterventionInstruction[] = []
      for (let i = 0; i < rawInstructions.length; i++) {
        const item = rawInstructions[i]
        const type = item['type'] as string ?? 'ALERT'
        const rawParams = item['params'] as Record<string, string> ?? {}
        const params = new Map<string, string>()
        const keys = Object.keys(rawParams)
        for (let j = 0; j < keys.length; j++) {
          params.set(keys[j], rawParams[keys[j]])
        }
        const description = item['description'] as string ?? type
        instructions.push(new InterventionInstruction(type as string, params, description))
      }
      return new QueryInterventionStatusResponse(tripId, riskLevel, instructions)
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'parseInterventionResponse error: ' + JSON.stringify(e))
      return new QueryInterventionStatusResponse(this.tripId, RiskLevel.NONE, [])
    }
  }

  private parseRiskStatusResponse(json: string): GetDriverRiskStatusResponse {
    try {
      const data = JSON.parse(json) as Record<string, Object>
      const activeAlerts = data['activeAlerts'] as Array<Record<string, string>> ?? []
      let riskLevel = RiskLevel.NONE
      let riskDescription = '正常驾驶'
      if (activeAlerts.length > 0) {
        let hasL3 = false
        let hasL2 = false
        for (let i = 0; i < activeAlerts.length; i++) {
          const rl = activeAlerts[i]['riskLevel'] ?? ''
          if (rl === 'L3_CRITICAL') {
            hasL3 = true
          }
          if (rl === 'L2_WARNING') {
            hasL2 = true
          }
        }
        if (hasL3) {
          riskLevel = RiskLevel.L3
        } else if (hasL2) {
          riskLevel = RiskLevel.L2
        } else {
          riskLevel = RiskLevel.L1
        }
        riskDescription = '活跃告警: ' + activeAlerts.length + '条'
      }
      return new GetDriverRiskStatusResponse(this.driverId, riskLevel, riskDescription)
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'parseRiskStatusResponse error: ' + JSON.stringify(e))
      return new GetDriverRiskStatusResponse(this.driverId, RiskLevel.NONE, '未知')
    }
  }

  async getPendingFamilyRequest(): Promise<Record<string, Object> | null> {
    if (this.useMock) {
      return null
    }
    try {
      const request = http.createHttp()
      const response = await request.request(
        this.baseUrl + '/drivers/' + this.driverId + '/family-requests/pending',
        { method: http.RequestMethod.GET }
      )
      if (response.responseCode === 200) {
        const result = JSON.parse(response.result as string) as Record<string, Object>
        if (result['hasPendingRequest'] === true) {
          return result
        }
      }
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'getPendingFamilyRequest failed: ' + JSON.stringify(err))
    }
    return null
  }

  async respondFamilyRequest(requestId: string, action: string): Promise<boolean> {
    if (this.useMock) {
      return true
    }
    try {
      const request = http.createHttp()
      const body = JSON.stringify({ action: action })
      const response = await request.request(
        this.baseUrl + '/drivers/' + this.driverId + '/family-requests/' + requestId + '/respond',
        {
          method: http.RequestMethod.POST,
          header: { 'Content-Type': 'application/json' },
          extraData: body
        }
      )
      return response.responseCode === 200
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'respondFamilyRequest failed: ' + JSON.stringify(err))
    }
    return false
  }

  private mapOverrideType(type: string): string {
    if (type === 'STEER') {
      return 'STEER'
    }
    if (type === 'BRAKE') {
      return 'BRAKE'
    }
    if (type === 'ACCELERATE') {
      return 'ACCELERATE'
    }
    return type
  }
}
