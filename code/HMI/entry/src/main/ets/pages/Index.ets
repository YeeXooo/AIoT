import { RiskLevel } from '../model/RiskLevel'
import { InterventionInstruction } from '../model/InterventionInstruction'
import { InterventionType } from '../model/InterventionType'
import { OverrideType } from '../model/OverrideSignal'
import { SensorStatus } from '../model/SensorStatus'
import { FamilyAccessInfo } from '../model/FamilyAccessInfo'
import { OtaProgressInfo } from '../model/OtaProgressInfo'
import { HmiViewModel } from '../service/HmiViewModel'
import { SensorStatusBar } from '../component/SensorStatusBar'
import { RiskStatusBar } from '../component/RiskStatusBar'
import { AmbientLightBar } from '../component/AmbientLightBar'
import { InterventionAlertOverlay } from '../component/InterventionAlertOverlay'
import { RearSeatPip } from '../component/RearSeatPip'
import { ActionBar } from '../component/ActionBar'
import { FamilyAccessNotice } from '../component/FamilyAccessNotice'
import { EnvironmentAdjustmentCard } from '../component/EnvironmentAdjustmentCard'
import { OtaProgressCard } from '../component/OtaProgressCard'
import { DrivingInfoPanel } from '../component/DrivingInfoPanel'
import { SimulationPanel } from '../component/SimulationPanel'

@Entry
@Component
struct Index {
  @State riskLevel: RiskLevel = RiskLevel.NONE
  @State activeInstructions: InterventionInstruction[] = []
  @State sensorStatus: SensorStatus = SensorStatus.createDefault()
  @State sensorFailureAlert: boolean = false
  @State cameraOccluded: boolean = false
  @State familyAccess: FamilyAccessInfo | null = null
  @State familyNoticeVisible: boolean = false
  @State otaProgress: OtaProgressInfo | null = null
  @State lifeDetectedAlert: boolean = false
  @State emergencyAlert: boolean = false
  @State speed: number = 0

  private viewModel: HmiViewModel = new HmiViewModel()
  private speedTimer: number = -1

  aboutToAppear(): void {
    this.viewModel.init()
    this.viewModel.setCallbacks(
      () => { this.refreshFromViewModel() },
      () => { this.refreshFromViewModel() },
      () => { this.refreshFromViewModel() },
      () => { this.refreshFromViewModel() },
      () => { this.refreshFromViewModel() },
      () => { this.refreshFromViewModel() }
    )
    this.startSpeedSimulation()
  }

  aboutToDisappear(): void {
    if (this.speedTimer !== -1) {
      clearInterval(this.speedTimer)
    }
  }

  private startSpeedSimulation(): void {
    this.speed = 0
    let targetSpeed: number = 0
    let phase: number = 0
    const phases: number[][] = [
      [0, 60, 8000],
      [60, 120, 6000],
      [120, 60, 5000],
      [60, 0, 7000]
    ]
    const pauseBetween: number = 1500

    const runPhase = () => {
      if (phase >= phases.length) {
        phase = 0
      }
      const p = phases[phase]
      const fromSpeed: number = p[0]
      const toSpeed: number = p[1]
      const duration: number = p[2]
      const diff: number = toSpeed - fromSpeed
      const steps: number = Math.floor(duration / 80)
      let step: number = 0
      targetSpeed = fromSpeed

      if (this.speedTimer !== -1) {
        clearInterval(this.speedTimer)
      }

      this.speedTimer = setInterval(() => {
        step++
        const progress: number = step / steps
        const eased: number = progress < 0.5
          ? 2 * progress * progress
          : 1 - Math.pow(-2 * progress + 2, 2) / 2
        targetSpeed = Math.round(fromSpeed + diff * eased)
        this.speed = targetSpeed

        if (step >= steps) {
          this.speed = toSpeed
          clearInterval(this.speedTimer)
          this.speedTimer = -1
          phase++
          setTimeout(() => { runPhase() }, pauseBetween)
        }
      }, 80)
    }

    setTimeout(() => { runPhase() }, 500)
  }

  private refreshFromViewModel(): void {
    this.riskLevel = this.viewModel.riskLevel
    this.activeInstructions = this.viewModel.activeInstructions.slice()
    this.sensorStatus = this.viewModel.sensorStatus
    this.sensorFailureAlert = this.viewModel.sensorFailureAlert
    this.cameraOccluded = this.viewModel.cameraOccluded
    this.familyAccess = this.viewModel.familyAccess
    this.otaProgress = this.viewModel.otaProgress
    this.lifeDetectedAlert = this.viewModel.lifeDetectedAlert
    this.emergencyAlert = this.viewModel.emergencyAlert
  }

  private hasEnvironmentAdjustment(): boolean {
    for (let i = 0; i < this.activeInstructions.length; i++) {
      const t = this.activeInstructions[i].type
      if (t === InterventionType.AIR_CONDITIONING || t === InterventionType.AUDIO_PLAYBACK) {
        return true
      }
    }
    return false
  }

  build() {
    Stack() {
      Column() {
        SensorStatusBar({
          sensorStatus: this.sensorStatus,
          sensorFailureAlert: this.sensorFailureAlert
        })

        Row() {
          Column() {
            DrivingInfoPanel({
              riskLevel: this.riskLevel,
              speed: this.speed
            })
          }
          .layoutWeight(1)
          .height('100%')

          Column() {
            RearSeatPip({ cameraOccluded: this.cameraOccluded })
          }
          .width(200)
          .height('100%')
          .padding({ right: 24, top: 16, bottom: 16 })
        }
        .layoutWeight(1)

        FamilyAccessNotice({
          familyAccess: this.familyAccess,
          visible: this.familyNoticeVisible
        })

        AmbientLightBar({
          activeInstructions: this.activeInstructions,
          riskLevel: this.riskLevel
        })

        RiskStatusBar({ riskLevel: this.riskLevel })

        if (this.hasEnvironmentAdjustment()) {
          EnvironmentAdjustmentCard({ activeInstructions: this.activeInstructions })
        }

        if (this.otaProgress !== null) {
          OtaProgressCard({ otaProgress: this.otaProgress })
        }

        ActionBar({
          riskLevel: this.riskLevel,
          activeInstructions: this.activeInstructions,
          cameraOccluded: this.cameraOccluded,
          familyAccess: this.familyAccess,
          onOverrideSteer: () => { this.viewModel.reportOverride(OverrideType.STEER); this.refreshFromViewModel() },
          onOverrideBrake: () => { this.viewModel.reportOverride(OverrideType.BRAKE); this.refreshFromViewModel() },
          onOverrideAccelerate: () => { this.viewModel.reportOverride(OverrideType.ACCELERATE); this.refreshFromViewModel() },
          onOcclusionToggle: () => {
            if (this.cameraOccluded) {
              this.viewModel.removeCameraOcclusion()
            } else {
              this.viewModel.triggerCameraOcclusion()
            }
          },
          onFamilyAccept: () => { this.familyNoticeVisible = true },
          onFamilyReject: () => {
            this.familyAccess = null
            this.familyNoticeVisible = false
          }
        })

        SimulationPanel({
          onFatigueL2: () => { this.viewModel.simulateFatigueL2(); this.refreshFromViewModel() },
          onFatigueL3: () => { this.viewModel.simulateFatigueL3(); this.refreshFromViewModel() },
          onRoadRageL2: () => { this.viewModel.simulateRoadRageL2(); this.refreshFromViewModel() },
          onLifeDetectedL3: () => { this.viewModel.simulateLifeDetectedL3(); this.refreshFromViewModel() },
          onCollisionL3: () => { this.viewModel.simulateCollisionL3(); this.refreshFromViewModel() },
          onSensorFailure: () => { this.viewModel.simulateSensorFailure(); this.refreshFromViewModel() },
          onFamilyAccess: () => { this.viewModel.simulateFamilyAccess(); this.refreshFromViewModel() },
          onClearRisk: () => { this.viewModel.clearRisk(); this.refreshFromViewModel() }
        })
      }
      .width('100%')
      .height('100%')
      .linearGradient({
        direction: GradientDirection.Bottom,
        colors: [['#000000', 0.0], ['#050507', 0.3], ['#0A0A0C', 0.6], ['#1C1C1E', 1.0]]
      })

      InterventionAlertOverlay({
        riskLevel: this.riskLevel,
        activeInstructions: this.activeInstructions,
        sensorFailureAlert: this.sensorFailureAlert,
        lifeDetectedAlert: this.lifeDetectedAlert,
        emergencyAlert: this.emergencyAlert,
        onOverride: () => {
          this.viewModel.reportOverride(OverrideType.STEER)
          this.refreshFromViewModel()
        }
      })
    }
    .width('100%')
    .height('100%')
  }
}
