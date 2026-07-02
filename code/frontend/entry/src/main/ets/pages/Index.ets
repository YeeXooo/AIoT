/**
 * 启动过渡页 — logo 淡入 + 跳转判断登录态。
 * 取代原空 Column + onAppear。
 */
import { router } from '@kit.ArkUI'
import { sessionStore } from '../viewmodel/SessionStore'

@Entry
@Component
struct Index {
  @State logoOpacity: number = 0
  @State logoScale: number = 0.8

  aboutToAppear(): void {
    setTimeout(() => {
      this.logoOpacity = 1
      this.logoScale = 1
    }, 80)
    // 600ms 后路由
    setTimeout(() => {
      if (sessionStore.isLoggedIn) {
        router.replaceUrl({ url: 'pages/MainPage' })
      } else {
        router.replaceUrl({ url: 'pages/LoginPage' })
      }
    }, 720)
  }

  build() {
    Column() {
      Column({ space: 16 }) {
        Image($r('app.media.logo_mark'))
          .width(96)
          .height(96)
          .scale({ x: this.logoScale, y: this.logoScale })
          .opacity(this.logoOpacity)
          .animation({ duration: 520, curve: Curve.EaseOut })
        Text($r('app.string.app_name'))
          .fontSize($r('app.float.subtitle_font_size'))
          .fontColor($r('app.color.text_secondary'))
          .letterSpacing(2)
          .opacity(this.logoOpacity)
          .animation({ duration: 600, curve: Curve.EaseOut })
      }
      .justifyContent(FlexAlign.Center)
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.bg_page'))
    .justifyContent(FlexAlign.Center)
  }
}
