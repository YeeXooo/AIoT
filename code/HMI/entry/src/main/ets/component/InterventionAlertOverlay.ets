import { InterventionInstruction } from '../model/InterventionInstruction'
import { InterventionType } from '../model/InterventionType'
import { RiskLevel } from '../model/RiskLevel'

@Component
export struct InterventionAlertOverlay {
  @Prop riskLevel: RiskLevel = RiskLevel.NONE
  @Prop activeInstructions: InterventionInstruction[] = []
  @Prop sensorFailureAlert: boolean = false
  @Prop lifeDetectedAlert: boolean = false
  @Prop emergencyAlert: boolean = false
  onOverride: () => void = () => {}
  @State headerPulse: number = 1.0
  @State dotPulse: number = 1.0
  private headerTimer: number = -1
  private dotTimer: number = -1
  private headerGoingDown: boolean = true
  private dotGoingDown: boolean = true

  private hasType(type: InterventionType): boolean {
    for (let i = 0; i < this.activeInstructions.length; i++) {
      if (this.activeInstructions[i].type === type) { return true }
    }
    return false
  }

  private getVoiceMessage(): string {
    for (let i = 0; i < this.activeInstructions.length; i++) {
      if (this.activeInstructions[i].type === InterventionType.VOICE_BROADCAST) {
        return this.activeInstructions[i].description
      }
    }
    return ''
  }

  private shouldShowOverlay(): boolean {
    return this.riskLevel === RiskLevel.L3 ||
      this.sensorFailureAlert ||
      this.lifeDetectedAlert ||
      this.emergencyAlert
  }

  aboutToAppear(): void {
    this.startHeaderPulse()
    this.startDotPulse()
  }

  aboutToDisappear(): void {
    if (this.headerTimer !== -1) { clearInterval(this.headerTimer) }
    if (this.dotTimer !== -1) { clearInterval(this.dotTimer) }
  }

  private startHeaderPulse(): void {
    this.headerTimer = setInterval(() => {
      if (this.headerGoingDown) {
        this.headerPulse = Math.max(0.2, this.headerPulse - 0.05)
        if (this.headerPulse <= 0.2) { this.headerGoingDown = false }
      } else {
        this.headerPulse = Math.min(1.0, this.headerPulse + 0.05)
        if (this.headerPulse >= 1.0) { this.headerGoingDown = true }
      }
    }, 40)
  }

  private startDotPulse(): void {
    this.dotTimer = setInterval(() => {
      if (this.dotGoingDown) {
        this.dotPulse = Math.max(0.15, this.dotPulse - 0.04)
        if (this.dotPulse <= 0.15) { this.dotGoingDown = false }
      } else {
        this.dotPulse = Math.min(1.0, this.dotPulse + 0.04)
        if (this.dotPulse >= 1.0) { this.dotGoingDown = true }
      }
    }, 40)
  }

  build() {
    if (this.shouldShowOverlay()) {
      Stack() {
        Column({ space: 0 }) {
          Blank().layoutWeight(1)

          Column({ space: 24 }) {
            if (this.riskLevel === RiskLevel.L3) {
              Column({ space: 8 }) {
                Text('HIGH RISK')
                  .fontSize(22)
                  .fontColor('#FF453A')
                  .fontWeight(FontWeight.Bold)
                  .letterSpacing(8)
                  .textAlign(TextAlign.Center)
                  .shadow({ radius: 20, color: '#80FF453A', offsetX: 0, offsetY: 0 })
                  .opacity(this.headerPulse)
                  .animation({ duration: 40, curve: Curve.Linear })
                Row()
                  .width(48)
                  .height(3)
                  .borderRadius(1.5)
                  .backgroundColor('#FF453A')
                  .shadow({ radius: 12, color: '#80FF453A', offsetX: 0, offsetY: 0 })
                  .opacity(this.headerPulse)
                  .animation({ duration: 40, curve: Curve.Linear })
              }
              .alignItems(HorizontalAlign.Center)
            }

            if (this.hasType(InterventionType.VOICE_BROADCAST)) {
              Column() {
                Text(this.getVoiceMessage())
                  .fontSize(20)
                  .fontColor($r('app.color.hmi_text_primary'))
                  .fontWeight(FontWeight.Medium)
                  .textAlign(TextAlign.Center)
                  .lineHeight(30)
              }
              .width('100%')
              .padding({ left: 28, right: 28, top: 20, bottom: 20 })
              .borderRadius(16)
              .backgroundColor('rgba(255,69,58,0.12)')
              .border({ width: 1, color: 'rgba(255,69,58,0.2)' })
            }

            if (this.hasType(InterventionType.SEAT_VIBRATION)) {
              this.AlertIndicator('#FF9F0A', '#55FF9F0A', 'Seat Vibration Active')
            }

            if (this.hasType(InterventionType.HAZARD_LIGHTS)) {
              this.AlertIndicator('#FFD60A', '#55FFD60A', 'Hazard Lights ON')
            }

            if (this.sensorFailureAlert) {
              Column() {
                Text('Safety monitoring system offline. Please drive with caution.')
                  .fontSize(15)
                  .fontColor('#FF453A')
                  .fontWeight(FontWeight.Medium)
                  .textAlign(TextAlign.Center)
                  .lineHeight(24)
              }
              .width('100%')
              .padding({ left: 28, right: 28, top: 16, bottom: 16 })
              .borderRadius(16)
              .backgroundColor('rgba(255,69,58,0.08)')
              .border({ width: 1, color: 'rgba(255,69,58,0.15)' })
            }

            if (this.lifeDetectedAlert) {
              this.AlertIndicator('#FF453A', '#55FF453A', 'Life Detected in Locked Vehicle')
            }

            if (this.emergencyAlert) {
              this.AlertIndicator('#FF453A', '#55FF453A', 'Emergency Rescue Activated')
            }

            if (this.hasType(InterventionType.NAVIGATE_TO_SHOULDER)) {
              Text('PULL OVER SAFELY')
                .fontSize(20)
                .fontColor($r('app.color.hmi_text_primary'))
                .fontWeight(FontWeight.Bold)
                .letterSpacing(4)
                .textAlign(TextAlign.Center)
            }

            Button() {
              Text('OVERRIDE')
                .fontSize(16)
                .fontColor($r('app.color.hmi_bg_dark'))
                .fontWeight(FontWeight.Bold)
                .letterSpacing(4)
            }
            .height(56)
            .borderRadius(28)
            .backgroundColor($r('app.color.risk_orange'))
            .shadow({ radius: 16, color: '#66FF9F0A', offsetX: 0, offsetY: 4 })
            .margin({ top: 4 })
            .onClick(() => { this.onOverride() })
          }
          .width('86%')
          .padding({ left: 28, right: 28, top: 36, bottom: 36 })
          .borderRadius(28)
          .backgroundColor('rgba(28,28,30,0.9)')
          .border({ width: 1, color: 'rgba(255,255,255,0.1)' })

          Blank().layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .width('100%')
      .height('100%')
      .backgroundColor('rgba(0,0,0,0.55)')
      .transition(
        TransitionEffect.OPACITY
          .combine(TransitionEffect.scale({ x: 0.9, y: 0.9 }))
          .animation({ duration: 400, curve: Curve.EaseOut })
      )
    }
  }

  @Builder
  AlertIndicator(dotColor: string, glowColor: string, message: string) {
    Row({ space: 12 }) {
      Row()
        .width(8)
        .height(8)
        .borderRadius(4)
        .backgroundColor(dotColor)
        .shadow({ radius: 10, color: glowColor, offsetX: 0, offsetY: 0 })
        .opacity(this.dotPulse)
        .animation({ duration: 40, curve: Curve.Linear })
      Text(message)
        .fontSize(15)
        .fontColor($r('app.color.hmi_text_secondary'))
        .fontWeight(FontWeight.Medium)
    }
  }
}
