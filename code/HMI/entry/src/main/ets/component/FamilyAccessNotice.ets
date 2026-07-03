import { FamilyAccessInfo } from '../model/FamilyAccessInfo'

@Component
export struct FamilyAccessNotice {
  @Prop familyAccess: FamilyAccessInfo | null = null
  visible: boolean = false

  build() {
    if (this.visible && this.familyAccess !== null) {
      Row({ space: 12 }) {
        Row()
          .width(10)
          .height(10)
          .borderRadius(5)
          .backgroundColor(this.familyAccess.granted ? $r('app.color.hmi_accent_blue') : $r('app.color.hmi_text_tertiary'))
          .animation({ duration: 500, curve: Curve.EaseInOut })
          .shadow({
            radius: this.familyAccess.granted ? 10 : 0,
            color: this.familyAccess.granted ? '#440A84FF' : 'transparent',
            offsetX: 0,
            offsetY: 0
          })

        if (this.familyAccess.granted) {
          Text('Family Call Active')
            .fontSize(14)
            .fontColor($r('app.color.hmi_text_primary'))
            .fontWeight(FontWeight.Medium)
            .letterSpacing(0.3)
        } else {
          Text('Call Ended')
            .fontSize(14)
            .fontColor($r('app.color.hmi_text_secondary'))
        }
      }
      .width('100%')
      .height(44)
      .padding({ left: 24, right: 24 })
      .borderRadius(14)
      .backgroundColor(this.familyAccess.granted ? 'rgba(10,132,255,0.1)' : 'rgba(28,28,30,0.5)')
      .alignItems(VerticalAlign.Center)
      .transition(
        TransitionEffect.OPACITY
          .combine(TransitionEffect.translate({ y: -16 }))
          .animation({ duration: 400, curve: Curve.EaseOut })
      )
    }
  }
}
