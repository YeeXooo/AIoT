import { RiskLevel } from '../model/RiskLevel'

@Component
export struct DrivingInfoPanel {
  @Prop riskLevel: RiskLevel = RiskLevel.NONE
  @Prop speed: number = 0
  @State breathValue: number = 1.0
  private breathTimer: number = -1
  private breathGoingDown: boolean = true

  private getSpeedColor(): string {
    if (this.riskLevel === RiskLevel.L3) { return '#FF453A' }
    if (this.riskLevel === RiskLevel.L2) { return '#FF9F0A' }
    if (this.speed > 100) { return '#FF453A' }
    if (this.speed > 80) { return '#FF9F0A' }
    return '#30D158'
  }

  private getGlowColor(): string {
    if (this.riskLevel === RiskLevel.L3 || this.speed > 100) { return 'rgba(255,69,58,' }
    if (this.riskLevel === RiskLevel.L2 || this.speed > 80) { return 'rgba(255,159,10,' }
    return 'rgba(48,209,88,'
  }

  aboutToAppear(): void {
    this.startBreathing()
  }

  aboutToDisappear(): void {
    if (this.breathTimer !== -1) { clearInterval(this.breathTimer) }
  }

  private startBreathing(): void {
    this.breathTimer = setInterval(() => {
      if (this.breathGoingDown) {
        this.breathValue = Math.max(0.0, this.breathValue - 0.02)
        if (this.breathValue <= 0.0) { this.breathGoingDown = false }
      } else {
        this.breathValue = Math.min(1.0, this.breathValue + 0.02)
        if (this.breathValue >= 1.0) { this.breathGoingDown = true }
      }
    }, 30)
  }

  build() {
    Stack() {
      Row()
        .width(280)
        .height(280)
        .borderRadius(140)
        .radialGradient({
          center: [140, 140],
          radius: 140,
          colors: [[this.getGlowColor() + (this.breathValue * 0.35).toString() + ')', 0.0],
            [this.getGlowColor() + '0.0)', 1.0]]
        })
        .animation({ duration: 30, curve: Curve.Linear })

      Column({ space: 0 }) {
        Text(this.speed.toString())
          .fontSize(140)
          .fontColor(this.getSpeedColor())
          .fontWeight(FontWeight.Lighter)
          .lineHeight(140)
          .textAlign(TextAlign.Center)

        Text('km/h')
          .fontSize(14)
          .fontColor($r('app.color.hmi_text_secondary'))
          .letterSpacing(6)
          .fontWeight(FontWeight.Medium)
          .textAlign(TextAlign.Center)
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.Center)

      Row({ space: 64 }) {
        Column({ space: 6 }) {
          Text('--')
            .fontSize(20)
            .fontColor($r('app.color.hmi_text_secondary'))
            .fontWeight(FontWeight.Lighter)
          Text('NAV')
            .fontSize(10)
            .fontColor($r('app.color.hmi_text_tertiary'))
            .letterSpacing(3)
            .fontWeight(FontWeight.Medium)
        }
        .alignItems(HorizontalAlign.Center)

        Column({ space: 6 }) {
          Text('--')
            .fontSize(20)
            .fontColor($r('app.color.hmi_text_secondary'))
            .fontWeight(FontWeight.Lighter)
          Text('RANGE')
            .fontSize(10)
            .fontColor($r('app.color.hmi_text_tertiary'))
            .letterSpacing(3)
            .fontWeight(FontWeight.Medium)
        }
        .alignItems(HorizontalAlign.Center)

        Column({ space: 6 }) {
          Text('--:--')
            .fontSize(20)
            .fontColor($r('app.color.hmi_text_secondary'))
            .fontWeight(FontWeight.Lighter)
          Text('DRIVE')
            .fontSize(10)
            .fontColor($r('app.color.hmi_text_tertiary'))
            .letterSpacing(3)
            .fontWeight(FontWeight.Medium)
        }
        .alignItems(HorizontalAlign.Center)
      }
      .margin({ top: 180 })
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Center)
  }
}
