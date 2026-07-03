import { RiskLevel } from '../model/RiskLevel'

@Component
export struct RiskStatusBar {
  @Prop riskLevel: RiskLevel = RiskLevel.NONE
  @State breathOpacity: number = 1.0
  private breathTimer: number = -1
  private breathGoingDown: boolean = true

  aboutToAppear(): void {
    this.startBreathing()
  }

  aboutToDisappear(): void {
    if (this.breathTimer !== -1) { clearInterval(this.breathTimer) }
  }

  private startBreathing(): void {
    this.breathTimer = setInterval(() => {
      if (this.breathGoingDown) {
        this.breathOpacity = Math.max(0.3, this.breathOpacity - 0.03)
        if (this.breathOpacity <= 0.3) { this.breathGoingDown = false }
      } else {
        this.breathOpacity = Math.min(1.0, this.breathOpacity + 0.03)
        if (this.breathOpacity >= 1.0) { this.breathGoingDown = true }
      }
    }, 50)
  }

  build() {
    Stack() {
      Row()
        .width('100%')
        .height(4)
        .borderRadius(2)
        .backgroundColor(this.getRiskColor())
        .opacity(this.riskLevel === RiskLevel.NONE ? 0.6 : this.breathOpacity * 0.5 + 0.5)
        .animation({ duration: 50, curve: Curve.Linear })
        .shadow({
          radius: this.getShadowRadius(),
          color: this.getShadowColor(),
          offsetX: 0,
          offsetY: 0
        })
    }
    .width('100%')
    .clip(true)
    .alignContent(Alignment.BottomStart)
  }

  private getRiskColor(): ResourceColor {
    if (this.riskLevel === RiskLevel.L3) { return '#FF453A' }
    if (this.riskLevel === RiskLevel.L2) { return '#FF9F0A' }
    if (this.riskLevel === RiskLevel.L1) { return '#FFD60A' }
    return '#30D158'
  }

  private getShadowRadius(): number {
    if (this.riskLevel === RiskLevel.L3) { return 24 }
    if (this.riskLevel === RiskLevel.L2) { return 14 }
    if (this.riskLevel === RiskLevel.L1) { return 8 }
    return 4
  }

  private getShadowColor(): ResourceColor {
    if (this.riskLevel === RiskLevel.L3) { return '#80FF453A' }
    if (this.riskLevel === RiskLevel.L2) { return '#60FF9F0A' }
    if (this.riskLevel === RiskLevel.L1) { return '#40FFD60A' }
    return '#2030D158'
  }
}
