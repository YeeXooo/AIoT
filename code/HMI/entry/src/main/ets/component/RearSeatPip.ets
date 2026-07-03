@Component
export struct RearSeatPip {
  @Prop cameraOccluded: boolean = false
  @State occludePulse: number = 1.0
  private pulseTimer: number = -1
  private pulseGoingDown: boolean = true

  aboutToAppear(): void {
    if (this.cameraOccluded) { this.startPulse() }
  }

  aboutToDisappear(): void {
    if (this.pulseTimer !== -1) { clearInterval(this.pulseTimer) }
  }

  private startPulse(): void {
    if (this.pulseTimer !== -1) { clearInterval(this.pulseTimer) }
    this.pulseTimer = setInterval(() => {
      if (this.pulseGoingDown) {
        this.occludePulse = Math.max(0.2, this.occludePulse - 0.04)
        if (this.occludePulse <= 0.2) { this.pulseGoingDown = false }
      } else {
        this.occludePulse = Math.min(1.0, this.occludePulse + 0.04)
        if (this.occludePulse >= 1.0) { this.pulseGoingDown = true }
      }
    }, 40)
  }

  build() {
    Stack() {
      if (this.cameraOccluded) {
        Column({ space: 12 }) {
          Row()
            .width(48)
            .height(48)
            .borderRadius(24)
            .backgroundColor('rgba(255,69,58,0.15)')
            .border({ width: 1.5, color: '#FF453A' })
            .shadow({ radius: 16, color: '#66FF453A', offsetX: 0, offsetY: 0 })
            .opacity(this.occludePulse)
            .animation({ duration: 40, curve: Curve.Linear })

          Text('CAMERA\nOCCLUDED')
            .fontSize(10)
            .fontColor('#FF453A')
            .fontWeight(FontWeight.Medium)
            .letterSpacing(2)
            .textAlign(TextAlign.Center)
            .lineHeight(15)
        }
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .onAppear(() => { this.startPulse() })
        .onDisAppear(() => {
          if (this.pulseTimer !== -1) { clearInterval(this.pulseTimer) }
        })
      } else {
        Column({ space: 12 }) {
          Row()
            .width(48)
            .height(48)
            .borderRadius(24)
            .backgroundColor('rgba(255,255,255,0.03)')
            .border({ width: 1, color: 'rgba(255,255,255,0.08)' })

          Text('REAR IR')
            .fontSize(10)
            .fontColor($r('app.color.hmi_text_tertiary'))
            .letterSpacing(3)
            .fontWeight(FontWeight.Medium)
        }
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }
    }
    .width('100%')
    .height('100%')
    .borderRadius(20)
    .backgroundColor('rgba(28,28,30,0.4)')
    .border({ width: 1, color: 'rgba(255,255,255,0.06)' })
  }
}
