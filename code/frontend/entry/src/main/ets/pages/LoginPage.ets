import { router } from '@kit.ArkUI'
import { authApi } from '../api/AuthApi'
import { sessionStore } from '../viewmodel/SessionStore'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0502
const TAG = 'LoginPage'

@Entry
@Component
struct LoginPage {
  @State account: string = 'family001'
  @State password: string = '123456'
  @State isLoading: boolean = false
  @State errorMsg: string = ''
  @State showPassword: boolean = false
  @State logoScale: number = 0.6
  @State logoOpacity: number = 0
  @State cardOffsetY: number = 60
  @State cardOpacity: number = 0
  @State glowRadius: number = 40
  @State glowOpacity: number = 0.2
  @State accountFocused: boolean = false
  @State passwordFocused: boolean = false

  private get canSubmit(): boolean {
    return !this.isLoading && this.account.trim().length > 0 && this.password.length > 0
  }

  private validate(): string {
    const acc = this.account.trim()
    if (acc.length === 0) return '请输入账号'
    if (this.password.length === 0) return '请输入密码'
    if (this.password.length < 6) return '密码至少 6 位'
    return ''
  }

  private async doLogin(): Promise<void> {
    const err = this.validate()
    if (err.length > 0) {
      this.errorMsg = err
      return
    }
    this.errorMsg = ''
    this.isLoading = true
    try {
      const loginReq: Record<string, string> = {
        'authMethod': 'PASSWORD',
        'credential': this.account.trim(),
        'secret': this.password,
      }
      hilog.info(DOMAIN, TAG, 'doLogin start, account=%{public}s', this.account.trim())
      const resp = await authApi.login(loginReq)
      hilog.info(DOMAIN, TAG, 'login resp success=%{public}s, status=%{public}d', String(resp.success), resp.status)
      if (resp.success && resp.data !== undefined) {
        hilog.info(DOMAIN, TAG, 'login data accountId=%{public}s role=%{public}s', resp.data.accountId, resp.data.role)
        await sessionStore.saveSession(
          resp.data.accessToken,
          resp.data.accountId,
          resp.data.role,
        )
        hilog.info(DOMAIN, TAG, 'session saved, navigating to MainPage')
        router.replaceUrl({ url: 'pages/MainPage' })
        hilog.info(DOMAIN, TAG, 'replaceUrl called')
      } else {
        const msg = (resp.error !== undefined && resp.error.message.length > 0)
          ? resp.error.message : '登录失败，请检查账号密码'
        hilog.error(DOMAIN, TAG, 'login failed: %{public}s', msg)
        this.errorMsg = msg
      }
    } catch (_e) {
      hilog.error(DOMAIN, TAG, 'doLogin exception: %{public}s', String(_e))
      this.errorMsg = '网络不可达，请检查网络连接'
    } finally {
      this.isLoading = false
    }
  }

  aboutToAppear(): void {
    setTimeout(() => {
      this.logoScale = 1
      this.logoOpacity = 1
      this.glowRadius = 70
      this.glowOpacity = 0.35
    }, 150)
    setTimeout(() => {
      this.cardOffsetY = 0
      this.cardOpacity = 1
    }, 400)
  }

  build() {
    Stack() {
      Column()
        .width('100%')
        .height('100%')
        .linearGradient({
          direction: GradientDirection.Bottom,
          colors: [[$r('app.color.page_gradient_top'), 0.0], [$r('app.color.page_gradient_mid'), 0.4], [$r('app.color.page_gradient_bottom'), 1.0]]
        })

      Scroll() {
        Column() {
          Column({ space: 6 }) {
            Stack() {
              Circle()
                .width(120)
                .height(120)
                .fill($r('app.color.logo_glow'))
                .opacity(this.glowOpacity)
                .blur(this.glowRadius)
                .animation({ duration: 800, curve: Curve.EaseOut })

              Image($r('app.media.logo_mark'))
                .width(72)
                .height(72)
                .scale({ x: this.logoScale, y: this.logoScale })
                .opacity(this.logoOpacity)
                .animation({ duration: 600, curve: Curve.EaseOut })
                .sharedTransition('logo_shared', { duration: 600, curve: Curve.EaseInOut })
            }
            .width(120)
            .height(120)

            Row({ space: 8 }) {
              Circle()
                .width(6)
                .height(6)
                .fill($r('app.color.accent_blue'))
              Text($r('app.string.app_name'))
                .fontSize($r('app.float.caption_font_size'))
                .fontColor($r('app.color.text_muted'))
                .letterSpacing(3)
            }
            .opacity(this.logoOpacity)
            .animation({ duration: 700, curve: Curve.EaseOut, delay: 200 })
          }
          .width('100%')
          .padding({ top: 56, bottom: 20 })
          .alignItems(HorizontalAlign.Center)

          Column({ space: 10 }) {
            Text($r('app.string.login_title'))
              .fontSize(28)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_on_dark'))
              .letterSpacing(0.5)
            Text($r('app.string.login_subtitle'))
              .fontSize($r('app.float.subtitle_font_size'))
              .fontColor($r('app.color.text_muted'))
          }
          .width('100%')
          .padding({ left: 40, right: 40, top: 16 })
          .alignItems(HorizontalAlign.Start)
          .opacity(this.cardOpacity)
          .translate({ y: this.cardOffsetY })
          .animation({ duration: 500, curve: Curve.EaseOut })

          Column({ space: 20 }) {
            Column({ space: 8 }) {
              Text('账号')
                .fontSize(11)
                .fontColor($r('app.color.text_muted'))
                .letterSpacing(1)
              Stack({ alignContent: Alignment.BottomStart }) {
                Column()
                  .width(this.accountFocused ? '100%' : '0%')
                  .height(2)
                  .backgroundColor($r('app.color.input_border_focus'))
                  .animation({ duration: 300, curve: Curve.EaseOut })
                TextInput({ placeholder: $r('app.string.login_hint_account'), text: $$this.account })
                  .fontSize($r('app.float.body_font_size'))
                  .fontColor($r('app.color.text_on_dark'))
                  .placeholderColor($r('app.color.input_placeholder'))
                  .backgroundColor($r('app.color.input_bg'))
                  .height(48)
                  .borderRadius(10)
                  .padding({ left: 14, right: 14 })
                  .border({
                    width: 1,
                    color: this.accountFocused ? $r('app.color.input_border_focus') : $r('app.color.input_border')
                  })
                  .animation({ duration: 200 })
                  .onFocus(() => {
                    this.errorMsg = ''
                    this.accountFocused = true
                  })
                  .onBlur(() => { this.accountFocused = false })
              }
              .width('100%')
            }
            .alignItems(HorizontalAlign.Start)

            Column({ space: 8 }) {
              Text('密码')
                .fontSize(11)
                .fontColor($r('app.color.text_muted'))
                .letterSpacing(1)
              Stack({ alignContent: Alignment.BottomStart }) {
                Column()
                  .width(this.passwordFocused ? '100%' : '0%')
                  .height(2)
                  .backgroundColor($r('app.color.input_border_focus'))
                  .animation({ duration: 300, curve: Curve.EaseOut })
                Row() {
                  TextInput({
                    placeholder: $r('app.string.login_hint_password'),
                    text: $$this.password,
                  })
                    .fontSize($r('app.float.body_font_size'))
                    .fontColor($r('app.color.text_on_dark'))
                    .placeholderColor($r('app.color.input_placeholder'))
                    .type(this.showPassword ? InputType.Normal : InputType.Password)
                    .backgroundColor($r('app.color.input_bg'))
                    .height(48)
                    .borderRadius(10)
                    .padding({ left: 14, right: 44 })
                    .border({
                      width: 1,
                      color: this.passwordFocused ? $r('app.color.input_border_focus') : $r('app.color.input_border')
                    })
                    .animation({ duration: 200 })
                    .layoutWeight(1)
                    .onFocus(() => {
                      this.errorMsg = ''
                      this.passwordFocused = true
                    })
                    .onBlur(() => { this.passwordFocused = false })
                  Text(this.showPassword ? '隐' : '显')
                    .fontSize(11)
                    .fontColor($r('app.color.text_muted'))
                    .width(36)
                    .height(48)
                    .textAlign(TextAlign.Center)
                    .onClick(() => { this.showPassword = !this.showPassword })
                }
              }
              .width('100%')
            }
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .padding({ left: 40, right: 40 })
          .margin({ top: 28 })
          .opacity(this.cardOpacity)
          .translate({ y: this.cardOffsetY })
          .animation({ duration: 500, curve: Curve.EaseOut, delay: 100 })

          if (this.errorMsg.length > 0) {
            Row({ space: 6 }) {
              Text('⚠')
                .fontSize(12)
                .fontColor($r('app.color.error_text'))
              Text(this.errorMsg)
                .fontSize(11)
                .fontColor($r('app.color.error_text'))
            }
            .width('80%')
            .margin({ top: 14 })
            .justifyContent(FlexAlign.Start)
            .transition({ type: TransitionType.Insert, opacity: 0, translate: { y: -10 } })
            .animation({ duration: 300, curve: Curve.EaseOut })
          }

          Button() {
            if (this.isLoading) {
              Row({ space: 8 }) {
                LoadingProgress()
                  .width(20)
                  .height(20)
                  .color(Color.White)
                Text('登录中…')
                  .fontSize($r('app.float.subtitle_font_size'))
                  .fontWeight(FontWeight.Medium)
                  .fontColor(Color.White)
              }
            } else {
              Text($r('app.string.login_button'))
                .fontSize($r('app.float.subtitle_font_size'))
                .fontWeight(FontWeight.Medium)
                .fontColor(Color.White)
            }
          }
          .width('80%')
          .height(50)
          .linearGradient({
            angle: 135,
            colors: [[$r('app.color.login_button_start'), 0.0], [$r('app.color.login_button_end'), 1.0]]
          })
          .borderRadius(12)
          .margin({ top: 28 })
          .enabled(this.canSubmit)
          .opacity(this.canSubmit ? 1 : 0.4)
          .scale({ x: this.canSubmit ? 1 : 0.98, y: this.canSubmit ? 1 : 0.98 })
          .animation({ duration: 200, curve: Curve.EaseOut })
          .shadow({
            radius: this.canSubmit ? 16 : 0,
            color: $r('app.color.login_button_shadow'),
            offsetX: 0,
            offsetY: 4
          })
          .animation({ duration: 300 })
          .onClick(() => this.doLogin())
          .opacity(this.cardOpacity)
          .translate({ y: this.cardOffsetY })
          .animation({ duration: 500, curve: Curve.EaseOut, delay: 200 })

          Blank()

          Text('© 2026 安全监测')
            .fontSize(11)
            .fontColor($r('app.color.copyright_text'))
            .margin({ bottom: 32 })
        }
        .width('100%')
        .height('100%')
      }
      .scrollBar(BarState.Off)
      .edgeEffect(EdgeEffect.Spring)
      .width('100%')
      .height('100%')
    }
    .width('100%')
    .height('100%')
  }
}
