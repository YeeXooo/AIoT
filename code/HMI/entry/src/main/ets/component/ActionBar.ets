import { InterventionInstruction } from '../model/InterventionInstruction'
import { InterventionType } from '../model/InterventionType'
import { OverrideType } from '../model/OverrideSignal'
import { RiskLevel } from '../model/RiskLevel'
import { FamilyAccessInfo } from '../model/FamilyAccessInfo'

@Component
export struct ActionBar {
  @Prop riskLevel: RiskLevel = RiskLevel.NONE
  @Prop activeInstructions: InterventionInstruction[] = []
  @Prop cameraOccluded: boolean = false
  @Prop familyAccess: FamilyAccessInfo | null = null
  onOverrideSteer: () => void = () => {}
  onOverrideBrake: () => void = () => {}
  onOverrideAccelerate: () => void = () => {}
  onOcclusionToggle: () => void = () => {}
  onFamilyAccept: () => void = () => {}
  onFamilyReject: () => void = () => {}

  private getInterventionSummary(): string {
    if (this.activeInstructions.length === 0) {
      return ''
    }
    const parts: string[] = []
    for (let i = 0; i < this.activeInstructions.length; i++) {
      parts.push(this.activeInstructions[i].description)
    }
    return parts.join('  \u00B7  ')
  }

  build() {
    Column({ space: 0 }) {
      if (this.activeInstructions.length > 0) {
        Text(this.getInterventionSummary())
          .fontSize(12)
          .fontColor($r('app.color.hmi_text_tertiary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .padding({ left: 24, right: 24, top: 8, bottom: 8 })
          .transition(
            TransitionEffect.OPACITY.animation({ duration: 300, curve: Curve.EaseInOut })
          )
      }

      Row({ space: 10 }) {
        this.ActionBtn('STEER', $r('app.color.hmi_fill_primary'), 'rgba(255,255,255,0.75)', () => { this.onOverrideSteer() })
        this.ActionBtn('BRAKE', $r('app.color.hmi_fill_primary'), 'rgba(255,255,255,0.75)', () => { this.onOverrideBrake() })
        this.ActionBtn('ACCEL', $r('app.color.hmi_fill_primary'), 'rgba(255,255,255,0.75)', () => { this.onOverrideAccelerate() })

        Blank()

        this.ActionBtn(
          this.cameraOccluded ? 'RESTORE' : 'BLOCK CAM',
          this.cameraOccluded ? 'rgba(48,209,88,0.15)' : 'rgba(255,69,58,0.12)',
          this.cameraOccluded ? '#30D158' : '#FF453A',
          () => { this.onOcclusionToggle() }
        )
      }
      .width('100%')
      .padding({ left: 24, right: 24, top: 6, bottom: 10 })

      if (this.familyAccess !== null && !this.familyAccess.granted) {
        Row({ space: 14 }) {
          Column({ space: 2 }) {
            Text('家属对讲请求')
              .fontSize(14)
              .fontColor($r('app.color.hmi_text_primary'))
              .fontWeight(FontWeight.Medium)
            Text(this.familyAccess.accessType === '视频通话' ? '家属发起视频通话' : '家属发起语音对讲')
              .fontSize(12)
              .fontColor($r('app.color.hmi_text_tertiary'))
          }
          .layoutWeight(1)

          Button() {
            Text('拒绝')
              .fontSize(12)
              .fontColor($r('app.color.risk_red'))
              .letterSpacing(1)
              .fontWeight(FontWeight.Medium)
          }
            .height(36)
            .borderRadius(18)
            .backgroundColor('rgba(255,69,58,0.12)')
            .onClick(() => { this.onFamilyReject() })

          Button() {
            Text('接受')
              .fontSize(12)
              .fontColor($r('app.color.risk_green'))
              .letterSpacing(1)
              .fontWeight(FontWeight.Medium)
          }
            .height(36)
            .borderRadius(18)
            .backgroundColor('rgba(48,209,88,0.15)')
            .onClick(() => { this.onFamilyAccept() })
        }
        .width('100%')
        .padding({ left: 24, right: 24, top: 12, bottom: 12 })
        .borderRadius(16)
        .backgroundColor('rgba(10,132,255,0.08)')
        .border({ width: 1, color: 'rgba(10,132,255,0.12)' })
        .transition(
          TransitionEffect.SLIDE.animation({ duration: 400, curve: Curve.EaseOut })
        )
      }

      if (this.familyAccess !== null && this.familyAccess.granted) {
        Row({ space: 14 }) {
          Column({ space: 2 }) {
            Text('Family Access Request')
              .fontSize(14)
              .fontColor($r('app.color.hmi_text_primary'))
              .fontWeight(FontWeight.Medium)
            Text('Incoming call from family member')
              .fontSize(12)
              .fontColor($r('app.color.hmi_text_tertiary'))
          }
          .layoutWeight(1)

          Button() {
            Text('DECLINE')
              .fontSize(12)
              .fontColor($r('app.color.risk_red'))
              .letterSpacing(1)
              .fontWeight(FontWeight.Medium)
          }
            .height(36)
            .borderRadius(18)
            .backgroundColor('rgba(255,69,58,0.12)')
            .onClick(() => { this.onFamilyReject() })

          Button() {
            Text('ACCEPT')
              .fontSize(12)
              .fontColor($r('app.color.risk_green'))
              .letterSpacing(1)
              .fontWeight(FontWeight.Medium)
          }
            .height(36)
            .borderRadius(18)
            .backgroundColor('rgba(48,209,88,0.15)')
            .onClick(() => { this.onFamilyAccept() })
        }
        .width('100%')
        .padding({ left: 24, right: 24, top: 12, bottom: 12 })
        .borderRadius(16)
        .backgroundColor('rgba(10,132,255,0.08)')
        .border({ width: 1, color: 'rgba(10,132,255,0.12)' })
        .transition(
          TransitionEffect.SLIDE.animation({ duration: 400, curve: Curve.EaseOut })
        )
      }
    }
    .width('100%')
  }

  @Builder
  ActionBtn(label: string, bg: ResourceColor, textColor: ResourceColor, action: () => void) {
    Button() {
      Text(label)
        .fontSize(12)
        .fontColor(textColor)
        .letterSpacing(1)
        .fontWeight(FontWeight.Medium)
    }
    .height(40)
    .borderRadius(20)
    .backgroundColor(bg)
    .onClick(() => { action() })
  }
}
