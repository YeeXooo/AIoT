@Component
export struct SimulationPanel {
  @State expanded: boolean = false
  onFatigueL2: () => void = () => {}
  onFatigueL3: () => void = () => {}
  onRoadRageL2: () => void = () => {}
  onLifeDetectedL3: () => void = () => {}
  onCollisionL3: () => void = () => {}
  onSensorFailure: () => void = () => {}
  onFamilyAccess: () => void = () => {}
  onClearRisk: () => void = () => {}

  build() {
    Column({ space: 0 }) {
      Row() {
        Text('DEV')
          .fontSize(9)
          .fontColor($r('app.color.hmi_text_tertiary'))
          .letterSpacing(3)
          .fontWeight(FontWeight.Medium)
        Text(this.expanded ? ' \u25BE' : ' \u25B8')
          .fontSize(9)
          .fontColor($r('app.color.hmi_text_tertiary'))
      }
      .padding({ left: 24, right: 24, top: 8, bottom: 8 })
      .onClick(() => {
        this.expanded = !this.expanded
      })

      if (this.expanded) {
        Column({ space: 8 }) {
          Row({ space: 8 }) {
            this.DevBtn('Fatigue L2', 'rgba(255,159,10,0.2)', () => { this.onFatigueL2() })
            this.DevBtn('Fatigue L3', 'rgba(255,69,58,0.2)', () => { this.onFatigueL3() })
            this.DevBtn('Road Rage', 'rgba(255,159,10,0.2)', () => { this.onRoadRageL2() })
          }
          Row({ space: 8 }) {
            this.DevBtn('Life L3', 'rgba(255,69,58,0.2)', () => { this.onLifeDetectedL3() })
            this.DevBtn('Collision', 'rgba(255,69,58,0.2)', () => { this.onCollisionL3() })
            this.DevBtn('Sensor Fail', 'rgba(255,69,58,0.2)', () => { this.onSensorFailure() })
          }
          Row({ space: 8 }) {
            this.DevBtn('Family In', 'rgba(10,132,255,0.2)', () => { this.onFamilyAccess() })
            this.DevBtn('Clear', 'rgba(48,209,88,0.2)', () => { this.onClearRisk() })
          }
        }
        .width('100%')
        .padding({ left: 24, right: 24, bottom: 10 })
        .transition(
          TransitionEffect.OPACITY
            .combine(TransitionEffect.SLIDE)
            .animation({ duration: 250, curve: Curve.EaseOut })
        )
      }
    }
    .width('100%')
    .backgroundColor('rgba(28,28,30,0.5)')
  }

  @Builder
  DevBtn(label: string, bg: string, action: () => void) {
    Button() {
      Text(label)
        .fontSize(10)
        .fontColor($r('app.color.hmi_text_secondary'))
        .fontWeight(FontWeight.Medium)
    }
    .height(30)
    .borderRadius(10)
    .backgroundColor(bg)
    .layoutWeight(1)
    .onClick(() => { action() })
  }
}
