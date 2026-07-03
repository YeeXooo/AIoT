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
  private tripId: string = 'trip-default'
  private driverId: string = 'driver-default'
  private useMock: boolean = true

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
    if (this.useMock) {
      hilog.info(DOMAIN, TAG, 'reportOverride: ' + signal.type)
      return new ReportOverrideResponse(true, '覆盖信号已接受')
    }
    try {
      const request = http.createHttp()
      const body = JSON.stringify({
        type: signal.type,
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

  private parseInterventionResponse(_json: string): QueryInterventionStatusResponse {
    return new QueryInterventionStatusResponse(this.tripId, RiskLevel.NONE, [])
  }

  private parseRiskStatusResponse(_json: string): GetDriverRiskStatusResponse {
    return new GetDriverRiskStatusResponse(this.driverId, RiskLevel.NONE, '未知')
  }
}
