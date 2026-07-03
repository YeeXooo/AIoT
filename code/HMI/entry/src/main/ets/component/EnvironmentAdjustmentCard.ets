import { InterventionInstruction } from '../model/InterventionInstruction'
import { InterventionType } from '../model/InterventionType'

@Component
export struct EnvironmentAdjustmentCard {
  @Prop activeInstructions: InterventionInstruction[] = []

  private hasAirConditioning(): boolean {
    for (let i = 0; i < this.activeInstructions.length; i++) {
      if (this.activeInstructions[i].type === InterventionType.AIR_CONDITIONING) { return true }
    }
    return false
  }

  private hasAudioPlayback(): boolean {
    for (let i = 0; i < this.activeInstructions.length; i++) {
      if (this.activeInstructions[i].type === InterventionType.AUDIO_PLAYBACK) { return true }
    }
    return false
  }

  private getAcInfo(): string {
    for (let i = 0; i < this.activeInstructions.length; i++) {
      if (this.activeInstructions[i].type === InterventionType.AIR_CONDITIONING) {
        return this.activeInstructions[i].description
      }
    }
    return ''
  }

  private getAudioInfo(): string {
    for (let i = 0; i < this.activeInstructions.length; i++) {
      if (this.activeInstructions[i].type === InterventionType.AUDIO_PLAYBACK) {
        return this.activeInstructions[i].description
      }
    }
    return ''
  }

  build() {
    if (this.hasAirConditioning() || this.hasAudioPlayback()) {
      Row({ space: 24 }) {
        if (this.hasAirConditioning()) {
          Row({ space: 10 }) {
            Row()
              .width(32)
              .height(32)
              .borderRadius(10)
              .backgroundColor('rgba(100,210,255,0.15)')
              .shadow({ radius: 8, color: '#3364D2FF', offsetX: 0, offsetY: 0 })
            Text(this.getAcInfo())
              .fontSize(14)
              .fontColor($r('app.color.hmi_accent_teal'))
              .fontWeight(FontWeight.Medium)
          }
        }

        if (this.hasAudioPlayback()) {
          Row({ space: 10 }) {
            Row()
              .width(32)
              .height(32)
              .borderRadius(10)
              .backgroundColor('rgba(191,90,242,0.15)')
              .shadow({ radius: 8, color: '#33BF5AF2', offsetX: 0, offsetY: 0 })
            Text(this.getAudioInfo())
              .fontSize(14)
              .fontColor($r('app.color.hmi_accent_purple'))
              .fontWeight(FontWeight.Medium)
          }
        }
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
