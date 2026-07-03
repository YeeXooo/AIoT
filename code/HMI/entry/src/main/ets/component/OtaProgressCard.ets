import { OtaProgressInfo } from '../model/OtaProgressInfo'

@Component
export struct OtaProgressCard {
  @Prop otaProgress: OtaProgressInfo | null = null
  @State progressGlow: number = 0
  private glowTimer: number = -1
  private glowGoingUp: boolean = true

  aboutToAppear(): void {
    this.startGlow()
  }

  aboutToDisappear(): void {
    if (this.glowTimer !== -1) { clearInterval(this.glowTimer) }
  }

  private startGlow(): void {
    this.glowTimer = setInterval(() => {
      if (this.glowGoingUp) {
        this.progressGlow = Math.min(1.0, this.progressGlow + 0.025)
        if (this.progressGlow >= 1.0) { this.glowGoingUp = false }
      } else {
        this.progressGlow = Math.max(0.0, this.progressGlow - 0.025)
        if (this.progressGlow <= 0.0) { this.glowGoingUp = true }
      }
    }, 50)
  }

  build() {
    if (this.otaProgress !== null) {
      Column({ space: 8 }) {
        Row() {
          Text('OTA')
            .fontSize(11)
            .fontColor($r('app.color.hmi_text_tertiary'))
            .letterSpacing(3)
            .fontWeight(FontWeight.Medium)
          Blank()
          Text(this.otaProgress.percent.toString() + '%')
            .fontSize(14)
            .fontColor($r('app.color.hmi_accent_blue'))
            .fontWeight(FontWeight.Medium)
        }
        .width('100%')

        Stack() {
          Row()
            .width('100%')
            .height(4)
            .borderRadius(2)
            .backgroundColor('rgba(255,255,255,0.08)')

          Row()
            .width((this.otaProgress.percent / 100 * 100).toString() + '%')
            .height(4)
            .borderRadius(2)
            .backgroundColor($r('app.color.hmi_accent_blue'))
            .shadow({ radius: 8 + this.progressGlow * 10, color: '#440A84FF', offsetX: 0, offsetY: 0 })
            .animation({ duration: 50, curve: Curve.Linear })
        }
        .width('100%')
        .alignContent(Alignment.Start)

        Text(this.otaProgress.stage + '  \u00B7  ' + this.otaProgress.estimatedTimeRemaining)
          .fontSize(11)
          .fontColor($r('app.color.hmi_text_tertiary'))
      }
      .width('100%')
      .padding({ left: 24, right: 24, top: 12, bottom: 12 })
      .borderRadius(14)
      .backgroundColor('rgba(28,28,30,0.5)')
      .border({ width: 1, color: 'rgba(255,255,255,0.08)' })
      .transition(
        TransitionEffect.OPACITY
          .combine(TransitionEffect.translate({ y: 8 }))
          .animation({ duration: 400, curve: Curve.EaseOut })
      )
    }
  }
}
