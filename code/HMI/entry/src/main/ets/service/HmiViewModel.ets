import { DomainEvent, DomainEventType } from '../model/DomainEvent'
import { EdgeEventBus } from './EdgeEventBus'
import { EdgeService } from './EdgeService'
import { InterventionInstruction } from '../model/InterventionInstruction'
import { InterventionType } from '../model/InterventionType'
import { RiskLevel } from '../model/RiskLevel'
import { SensorStatus, SensorStatusItem } from '../model/SensorStatus'
import { FamilyAccessInfo } from '../model/FamilyAccessInfo'
import { OtaProgressInfo } from '../model/OtaProgressInfo'
import { OverrideSignal, OverrideType } from '../model/OverrideSignal'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0001
const TAG = 'HmiViewModel'

export class HmiViewModel {
  private eventBus: EdgeEventBus = EdgeEventBus.getInstance()
  private edgeService: EdgeService = new EdgeService()
  private pollTimer: number = -1

  riskLevel: RiskLevel = RiskLevel.NONE
  activeInstructions: InterventionInstruction[] = []
  sensorStatus: SensorStatus = SensorStatus.createDefault()
  familyAccess: FamilyAccessInfo | null = null
  otaProgress: OtaProgressInfo | null = null
  cameraOccluded: boolean = false
  sensorFailureAlert: boolean = false
  lifeDetectedAlert: boolean = false
  emergencyAlert: boolean = false

  private onRiskDetermined: (() => void) | null = null
  private onRiskResolved: (() => void) | null = null
  private onFamilyAccessChanged: (() => void) | null = null
  private onSensorStatusChanged: (() => void) | null = null
  private onCameraOcclusionChanged: (() => void) | null = null
  private onEmergencyChanged: (() => void) | null = null

  init(): void {
    this.eventBus.subscribe(DomainEventType.RISK_DETERMINED, (event: DomainEvent) => {
      this.handleRiskDetermined(event)
    })
    this.eventBus.subscribe(DomainEventType.RISK_RESOLVED, (event: DomainEvent) => {
      this.handleRiskResolved(event)
    })
    this.eventBus.subscribe(DomainEventType.FAMILY_ACCESS_GRANTED, (_event: DomainEvent) => {
      this.familyAccess = new FamilyAccessInfo(true, '家属', '对讲', Date.now())
      if (this.onFamilyAccessChanged !== null) {
        this.onFamilyAccessChanged()
      }
    })
    this.eventBus.subscribe(DomainEventType.FAMILY_ACCESS_REVOKED, (_event: DomainEvent) => {
      this.familyAccess = new FamilyAccessInfo(false, '', '', Date.now())
      if (this.onFamilyAccessChanged !== null) {
        this.onFamilyAccessChanged()
      }
    })
    this.eventBus.subscribe(DomainEventType.CAMERA_OCCLUSION_DETECTED, (_event: DomainEvent) => {
      this.cameraOccluded = true
      if (this.onCameraOcclusionChanged !== null) {
        this.onCameraOcclusionChanged()
      }
    })
    this.eventBus.subscribe(DomainEventType.CAMERA_OCCLUSION_REMOVED, (_event: DomainEvent) => {
      this.cameraOccluded = false
      if (this.onCameraOcclusionChanged !== null) {
        this.onCameraOcclusionChanged()
      }
    })
    this.eventBus.subscribe(DomainEventType.SENSOR_FAILURE, (_event: DomainEvent) => {
      this.sensorFailureAlert = true
      const now = Date.now()
      this.sensorStatus = new SensorStatus(
        new SensorStatusItem('摄像头', false, now),
        new SensorStatusItem('雷达', false, now),
        new SensorStatusItem('生理', false, now)
      )
      if (this.onSensorStatusChanged !== null) {
        this.onSensorStatusChanged()
      }
    })
    this.eventBus.subscribe(DomainEventType.LIFE_DETECTED, (_event: DomainEvent) => {
      this.lifeDetectedAlert = true
      if (this.onEmergencyChanged !== null) {
        this.onEmergencyChanged()
      }
    })
    this.eventBus.subscribe(DomainEventType.EMERGENCY_ACTIVATED, (_event: DomainEvent) => {
      this.emergencyAlert = true
      if (this.onEmergencyChanged !== null) {
        this.onEmergencyChanged()
      }
    })
    this.startPolling()
  }

  destroy(): void {
    this.stopPolling()
  }

  setCallbacks(
    onRiskDetermined: () => void,
    onRiskResolved: () => void,
    onFamilyAccessChanged: () => void,
    onSensorStatusChanged: () => void,
    onCameraOcclusionChanged: () => void,
    onEmergencyChanged: () => void
  ): void {
    this.onRiskDetermined = onRiskDetermined
    this.onRiskResolved = onRiskResolved
    this.onFamilyAccessChanged = onFamilyAccessChanged
    this.onSensorStatusChanged = onSensorStatusChanged
    this.onCameraOcclusionChanged = onCameraOcclusionChanged
    this.onEmergencyChanged = onEmergencyChanged
  }

  reportOverride(type: OverrideType): void {
    const signal = new OverrideSignal(type, Date.now())
    hilog.info(DOMAIN, TAG, 'Override reported: ' + type)
    this.edgeService.reportOverride(signal)
    this.riskLevel = RiskLevel.NONE
    this.activeInstructions = []
    if (this.onRiskResolved !== null) {
      this.onRiskResolved()
    }
  }

  acceptFamilyRequest(): void {
    if (this.familyAccess === null || !this.familyAccess.pending) {
      return
    }
    const requestId = this.familyAccess.requestId
    const accessType = this.familyAccess.accessType
    this.edgeService.respondFamilyRequest(requestId, 'accept')
    this.familyAccess = FamilyAccessInfo.createGranted(accessType)
    if (this.onFamilyAccessChanged !== null) {
      this.onFamilyAccessChanged()
    }
  }

  declineFamilyRequest(): void {
    if (this.familyAccess === null || !this.familyAccess.pending) {
      this.familyAccess = null
      if (this.onFamilyAccessChanged !== null) {
        this.onFamilyAccessChanged()
      }
      return
    }
    const requestId = this.familyAccess.requestId
    this.edgeService.respondFamilyRequest(requestId, 'decline')
    this.familyAccess = null
    if (this.onFamilyAccessChanged !== null) {
      this.onFamilyAccessChanged()
    }
  }

  triggerCameraOcclusion(): void {
    this.cameraOccluded = true
    const payload = new Map<string, string>()
    this.eventBus.publish(
      new DomainEvent(DomainEventType.CAMERA_OCCLUSION_DETECTED, payload, Date.now())
    )
  }

  removeCameraOcclusion(): void {
    this.cameraOccluded = false
    const payload = new Map<string, string>()
    this.eventBus.publish(
      new DomainEvent(DomainEventType.CAMERA_OCCLUSION_REMOVED, payload, Date.now())
    )
  }

  private startPolling(): void {
    this.pollTimer = setInterval(() => {
      this.edgeService.queryInterventionStatus().then((response) => {
        this.riskLevel = response.riskLevel
        this.activeInstructions = response.activeInstructions
        if (this.onRiskDetermined !== null && response.riskLevel !== RiskLevel.NONE) {
          this.onRiskDetermined()
        }
      }).catch((_e: Error) => {
        hilog.error(DOMAIN, TAG, 'Polling queryInterventionStatus failed')
      })

      this.edgeService.getDriverRiskStatus().then((response) => {
        if (response.riskLevel !== RiskLevel.NONE && this.riskLevel === RiskLevel.NONE) {
          this.riskLevel = response.riskLevel
        }
      }).catch((_e: Error) => {
        hilog.error(DOMAIN, TAG, 'Polling getDriverRiskStatus failed')
      })

      this.edgeService.getPendingFamilyRequest().then((req) => {
        if (req !== null) {
          if (this.familyAccess !== null && this.familyAccess.pending) {
            return
          }
          const requestId = req['requestId'] as string ?? ''
          const sessionType = req['sessionType'] as string ?? 'AUDIO'
          this.familyAccess = FamilyAccessInfo.createPending(requestId, sessionType)
          if (this.onFamilyAccessChanged !== null) {
            this.onFamilyAccessChanged()
          }
        }
      }).catch((_e: Error) => {
        hilog.error(DOMAIN, TAG, 'Polling getPendingFamilyRequest failed')
      })
    }, 1500)
  }

  private stopPolling(): void {
    if (this.pollTimer !== -1) {
      clearInterval(this.pollTimer)
      this.pollTimer = -1
    }
  }

  simulateFatigueL2(): void {
    this.riskLevel = RiskLevel.L2
    this.activeInstructions = [
      InterventionInstruction.createAmbientLight('ORANGE'),
      InterventionInstruction.createAlert()
    ]
    if (this.onRiskDetermined !== null) {
      this.onRiskDetermined()
    }
  }

  simulateFatigueL3(): void {
    this.riskLevel = RiskLevel.L3
    this.activeInstructions = [
      InterventionInstruction.createAmbientLight('RED'),
      InterventionInstruction.createVoiceBroadcast('您已疲劳驾驶，请停车休息'),
      InterventionInstruction.createSeatVibration()
    ]
    if (this.onRiskDetermined !== null) {
      this.onRiskDetermined()
    }
  }

  simulateRoadRageL2(): void {
    this.riskLevel = RiskLevel.L2
    this.activeInstructions = [
      InterventionInstruction.createAmbientLight('ORANGE'),
      InterventionInstruction.createAirConditioning(-2),
      InterventionInstruction.createAudioPlayback('舒缓音乐')
    ]
    if (this.onRiskDetermined !== null) {
      this.onRiskDetermined()
    }
  }

  simulateLifeDetectedL3(): void {
    this.riskLevel = RiskLevel.L3
    this.activeInstructions = [
      InterventionInstruction.createHazardLights(),
      InterventionInstruction.createVoiceBroadcast('车外鸣笛-短促')
    ]
    this.lifeDetectedAlert = true
    if (this.onRiskDetermined !== null) {
      this.onRiskDetermined()
    }
    if (this.onEmergencyChanged !== null) {
      this.onEmergencyChanged()
    }
  }

  simulateCollisionL3(): void {
    this.riskLevel = RiskLevel.L3
    this.activeInstructions = [
      InterventionInstruction.createHazardLights(),
      InterventionInstruction.createVoiceBroadcast('碰撞失能-车门已解锁')
    ]
    this.emergencyAlert = true
    if (this.onRiskDetermined !== null) {
      this.onRiskDetermined()
    }
    if (this.onEmergencyChanged !== null) {
      this.onEmergencyChanged()
    }
  }

  simulateSensorFailure(): void {
    this.sensorFailureAlert = true
    this.activeInstructions = [
      InterventionInstruction.createVoiceBroadcast('安全监测系统已失效，请注意驾驶安全')
    ]
    const now = Date.now()
    this.sensorStatus = new SensorStatus(
      new SensorStatusItem('摄像头', false, now),
      new SensorStatusItem('雷达', false, now),
      new SensorStatusItem('生理', false, now)
    )
    if (this.onSensorStatusChanged !== null) {
      this.onSensorStatusChanged()
    }
    if (this.onRiskDetermined !== null) {
      this.onRiskDetermined()
    }
  }

  simulateFamilyAccess(): void {
    this.familyAccess = new FamilyAccessInfo(true, '家属', '对讲', Date.now())
    if (this.onFamilyAccessChanged !== null) {
      this.onFamilyAccessChanged()
    }
  }

  simulateOtaProgress(stage: string, percent: number): void {
    this.otaProgress = new OtaProgressInfo(stage, percent, '预计' + (100 - percent).toString() + '秒')
  }

  clearRisk(): void {
    this.riskLevel = RiskLevel.NONE
    this.activeInstructions = []
    this.sensorFailureAlert = false
    this.lifeDetectedAlert = false
    this.emergencyAlert = false
    this.sensorStatus = SensorStatus.createDefault()
    if (this.onRiskResolved !== null) {
      this.onRiskResolved()
    }
    if (this.onSensorStatusChanged !== null) {
      this.onSensorStatusChanged()
    }
    if (this.onEmergencyChanged !== null) {
      this.onEmergencyChanged()
    }
  }

  private handleRiskDetermined(event: DomainEvent): void {
    const levelStr = event.payload.get('riskLevel')
    if (levelStr !== undefined) {
      if (levelStr === 'L1') {
        this.riskLevel = RiskLevel.L1
      } else if (levelStr === 'L2') {
        this.riskLevel = RiskLevel.L2
      } else if (levelStr === 'L3') {
        this.riskLevel = RiskLevel.L3
      }
    }
    if (this.onRiskDetermined !== null) {
      this.onRiskDetermined()
    }
  }

  private handleRiskResolved(_event: DomainEvent): void {
    this.riskLevel = RiskLevel.NONE
    this.activeInstructions = []
    if (this.onRiskResolved !== null) {
      this.onRiskResolved()
    }
  }
}
