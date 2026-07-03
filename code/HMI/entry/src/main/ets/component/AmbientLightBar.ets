import { InterventionInstruction } from '../model/InterventionInstruction'
import { InterventionType } from '../model/InterventionType'
import { RiskLevel } from '../model/RiskLevel'

@Component
export struct AmbientLightBar {
  @Prop activeInstructions: InterventionInstruction[] = []
  @Prop riskLevel: RiskLevel = RiskLevel.NONE
  @State breathGlow: number = 0
  private glowTimer: number = -1
  private glowGoingUp: boolean = true

  aboutToAppear(): void {
    this.startBreathing()
  }

  aboutToDisappear(): void {
    if (this.glowTimer !== -1) { clearInterval(this.glowTimer) }
  }

  private startBreathing(): void {
    this.glowTimer = setInterval(() => {
      if (this.glowGoingUp) {
        this.breathGlow = Math.min(1.0, this.breathGlow + 0.02)
        if (this.breathGlow >= 1.0) { this.glowGoingUp = false }
      } else {
        this.breathGlow = Math.max(0.0, this.breathGlow - 0.02)
        if (this.breathGlow <= 0.0) { this.glowGoingUp = true }
      }
    }, 50)
  }

  build() {
    Row()
      .width('100%')
      .height(3)
      .borderRadius(1.5)
      .backgroundColor(this.getAmbientColor())
      .shadow({
        radius: this.getGlowRadius() + this.breathGlow * this.getBreathDelta(),
        color: this.getGlowColor(),
        offsetX: 0,
        offsetY: 3
      })
      .animation({ duration: 50, curve: Curve.Linear })
  }

  private getAmbientColor(): ResourceColor {
    for (let i = 0; i < this.activeInstructions.length; i++) {
      const inst = this.activeInstructions[i]
      if (inst.type === InterventionType.AMBIENT_LIGHT_COLOR) {
        const color = inst.params.get('color')
        if (color !== undefined) {
          if (color === 'RED') { return '#FF453A' }
          if (color === 'ORANGE') { return '#FF9F0A' }
          return color
        }
      }
    }
    if (this.riskLevel === RiskLevel.L3) { return '#FF453A' }
    if (this.riskLevel === RiskLevel.L2) { return '#FF9F0A' }
    return 'transparent'
  }

  private getGlowRadius(): number {
    if (this.riskLevel === RiskLevel.L3) { return 30 }
    if (this.riskLevel === RiskLevel.L2) { return 15 }
    return 0
  }

  private getBreathDelta(): number {
    if (this.riskLevel === RiskLevel.L3) { return 30 }
    if (this.riskLevel === RiskLevel.L2) { return 15 }
    return 0
  }

  private getGlowColor(): string {
    if (this.riskLevel === RiskLevel.L3) { return '#80FF453A' }
    if (this.riskLevel === RiskLevel.L2) { return '#60FF9F0A' }
    return 'transparent'
  }
}
