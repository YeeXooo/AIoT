import { router } from '@kit.ArkUI'
import { sessionStore } from '../viewmodel/SessionStore'

@Entry
@Component
struct Index {
  @State logoOpacity: number = 0
  @State logoScale: number = 0.8
  @State glowRadius: number = 40
  @State glowOpacity: number = 0.25
  @State ringScale: number = 0.6
  @State ringOpacity: number = 0

  aboutToAppear(): void {
    setTimeout(() => {
      this.logoOpacity = 1
      this.logoScale = 1
      this.ringScale = 1
      this.ringOpacity = 0.15
    }, 100)
    setTimeout(() => {
      this.glowRadius = 80
      this.glowOpacity = 0.45
    }, 300)
    setTimeout(() => {
      if (sessionStore.isLoggedIn) {
        router.replaceUrl({ url: 'pages/MainPage' })
      } else {
        router.replaceUrl({ url: 'pages/LoginPage' })
      }
    }, 1200)
  }

  build() {
    Stack() {
      Column() {
        Column() {
          Column() {
            Circle()
              .width(160)
              .height(160)
              .fill($r('app.color.logo_glow'))
              .opacity(this.glowOpacity)
              .blur(this.glowRadius)
              .animation({ duration: 800, curve: Curve.EaseOut })
          }
          .width(160)
          .height(160)
          .justifyContent(FlexAlign.Center)
          .alignItems(HorizontalAlign.Center)

          Circle()
            .width(140)
            .height(140)
            .fill(Color.Transparent)
            .stroke($r('app.color.logo_stroke'))
            .strokeWidth(1.5)
            .opacity(this.ringOpacity)
            .scale({ x: this.ringScale, y: this.ringScale })
            .animation({ duration: 900, curve: Curve.EaseOut })
        }
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)

        Column({ space: 16 }) {
          Image($r('app.media.logo_mark'))
            .width(96)
            .height(96)
            .scale({ x: this.logoScale, y: this.logoScale })
            .opacity(this.logoOpacity)
            .animation({ duration: 600, curve: Curve.EaseOut })
            .sharedTransition('logo_shared', { duration: 600, curve: Curve.EaseInOut })
          Text($r('app.string.app_name'))
            .fontSize($r('app.float.subtitle_font_size'))
            .fontColor($r('app.color.text_secondary'))
            .letterSpacing(4)
            .opacity(this.logoOpacity)
            .animation({ duration: 700, curve: Curve.EaseOut, delay: 150 })
        }
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
    }
    .width('100%')
    .height('100%')
    .linearGradient({
      direction: GradientDirection.Bottom,
      colors: [[$r('app.color.page_gradient_top'), 0.0], [$r('app.color.page_gradient_mid'), 0.5], [$r('app.color.page_gradient_bottom'), 1.0]]
    })
  }
}
