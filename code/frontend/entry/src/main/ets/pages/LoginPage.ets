import { router } from '@kit.ArkUI'
import { authApi } from '../api/AuthApi'
import { sessionStore } from '../viewmodel/SessionStore'

@Entry
@Component
struct LoginPage {
  @State account: string = ''
  @State password: string = ''
  @State isLoading: boolean = false
  @State errorMsg: string = ''
  @State showPassword: boolean = false
  // logo 入场动画
  @State logoScale: number = 0.6
  @State logoOpacity: number = 0

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
      const resp = await authApi.login(loginReq)
      if (resp.success && resp.data !== undefined) {
        await sessionStore.saveSession(
          resp.data.accessToken,
          resp.data.accountId,
          resp.data.role,
        )
        router.replaceUrl({ url: 'pages/MainPage' })
      } else {
        const msg = (resp.error !== undefined && resp.error.message.length > 0)
          ? resp.error.message : '登录失败，请检查账号密码'
        this.errorMsg = msg
      }
    } catch (_e) {
      this.errorMsg = '网络不可达，请检查网络连接'
    } finally {
      this.isLoading = false
    }
  }

  aboutToAppear(): void {
    // logo 入场
    setTimeout(() => {
      this.logoScale = 1
      this.logoOpacity = 1
    }, 120)
  }

  build() {
    Column() {
      // 顶部品牌 logo 区
      Column({ space: 10 }) {
        Image($r('app.media.logo_mark'))
          .width(72)
          .height(72)
          .scale({ x: this.logoScale, y: this.logoScale })
          .opacity(this.logoOpacity)
          .animation({ duration: 480, curve: Curve.EaseOut })
        Row({ space: 8 }) {
          Text('●')
            .fontSize(12)
            .fontColor($r('app.color.brand_accent'))
          Text($r('app.string.app_name'))
            .fontSize($r('app.float.caption_font_size'))
            .fontColor($r('app.color.text_secondary'))
            .letterSpacing(2)
        }
      }
      .width('100%')
      .padding({ top: 64, bottom: 8 })
      .alignItems(HorizontalAlign.Center)

      // 标题区
      Column({ space: 8 }) {
        Text($r('app.string.login_title'))
          .fontSize(28)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
          .letterSpacing(0.5)
        Text($r('app.string.login_subtitle'))
          .fontSize($r('app.float.subtitle_font_size'))
          .fontColor($r('app.color.text_secondary'))
      }
      .width('100%')
      .padding({ left: 40, right: 40, top: 24 })
      .alignItems(HorizontalAlign.Start)

      // 表单区
      Column({ space: 18 }) {
        Column({ space: 8 }) {
          Text('账号')
            .fontSize(11)
            .fontColor($r('app.color.text_secondary'))
            .letterSpacing(1)
          TextInput({ placeholder: $r('app.string.login_hint_account'), text: $$this.account })
            .fontSize($r('app.float.body_font_size'))
            .fontColor($r('app.color.text_primary'))
            .placeholderColor($r('app.color.text_hint'))
            .backgroundColor($r('app.color.bg_card'))
            .height(48)
            .borderRadius(10)
            .padding({ left: 14, right: 14 })
            .border({ width: 1, color: $r('app.color.divider') })
            .onFocus(() => { this.errorMsg = '' })
        }
        .alignItems(HorizontalAlign.Start)

        Column({ space: 8 }) {
          Text('密码')
            .fontSize(11)
            .fontColor($r('app.color.text_secondary'))
            .letterSpacing(1)
          Row() {
            TextInput({
              placeholder: $r('app.string.login_hint_password'),
              text: $$this.password,
            })
              .fontSize($r('app.float.body_font_size'))
              .fontColor($r('app.color.text_primary'))
              .placeholderColor($r('app.color.text_hint'))
              .type(this.showPassword ? InputType.Normal : InputType.Password)
              .backgroundColor($r('app.color.bg_card'))
              .height(48)
              .borderRadius(10)
              .padding({ left: 14, right: 44 })
              .border({ width: 1, color: $r('app.color.divider') })
              .layoutWeight(1)
              .onFocus(() => { this.errorMsg = '' })
            Text(this.showPassword ? '隐' : '显')
              .fontSize(11)
              .fontColor($r('app.color.text_hint'))
              .width(36)
              .height(48)
              .textAlign(TextAlign.Center)
              .onClick(() => { this.showPassword = !this.showPassword })
          }
        }
        .alignItems(HorizontalAlign.Start)
      }
      .width('100%')
      .padding({ left: 40, right: 40 })
      .margin({ top: 36 })

      // 错误提示
      if (this.errorMsg.length > 0) {
        Row({ space: 6 }) {
          Text('⚠')
            .fontSize(12)
            .fontColor($r('app.color.status_red'))
          Text(this.errorMsg)
            .fontSize(11)
            .fontColor($r('app.color.status_red'))
        }
        .width('80%')
        .margin({ top: 12 })
        .justifyContent(FlexAlign.Start)
      }

      // 登录按钮（含 loading 态）
      Button() {
        if (this.isLoading) {
          Row({ space: 8 }) {
            LoadingProgress()
              .width(20)
              .height(20)
              .color($r('app.color.bg_card'))
            Text('登录中…')
              .fontSize($r('app.float.subtitle_font_size'))
              .fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.bg_card'))
          }
        } else {
          Text($r('app.string.login_button'))
            .fontSize($r('app.float.subtitle_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.bg_card'))
        }
      }
      .width('80%')
      .height(50)
      .backgroundColor($r('app.color.brand_primary'))
      .borderRadius(10)
      .margin({ top: 24 })
      .enabled(this.canSubmit)
      .opacity(this.canSubmit ? 1 : 0.45)
      .onClick(() => this.doLogin())

      Blank()

      Text('© 2026 安全监测')
        .fontSize(11)
        .fontColor($r('app.color.text_hint'))
        .margin({ bottom: 32 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.bg_page'))
  }
}
