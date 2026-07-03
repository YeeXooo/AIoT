import { AppCard } from '../../common/ui'
import { sessionStore } from '../../viewmodel/SessionStore'
import { themeStore } from '../../viewmodel/ThemeStore'
import { router } from '@kit.ArkUI'
import { promptAction } from '@kit.ArkUI'
import { ConfigurationConstant } from '@kit.AbilityKit'
import { common } from '@kit.AbilityKit'

@Component
export struct ProfileTab {
  @State userName: string = '家属用户'
  @State role: string = '家属端'
  @State phone: string = '138****6789'
  @State showSettings: boolean = false
  @State contentOpacity: number = 0
  @State contentOffsetY: number = 20
  @State logoutScale: number = 1.0
  @StorageProp('isDarkMode') isDarkMode: boolean = true
  private pushEnabled: boolean = true

  aboutToAppear(): void {
    const acc = sessionStore.account
    if (acc !== null) {
      this.userName = acc.role === 'FAMILY' ? '家属用户' : (acc.role === 'MANAGER' ? '车队管理员' : '救援专员')
    }
    setTimeout(() => {
      this.contentOpacity = 1
      this.contentOffsetY = 0
    }, 150)
  }

  private toggleSettings(): void {
    this.showSettings = !this.showSettings
  }

  private async toggleTheme(isDark: boolean): Promise<void> {
    await themeStore.toggle(isDark)
    try {
      const context = getContext(this) as common.UIAbilityContext
      context.getApplicationContext().setColorMode(
        isDark ? ConfigurationConstant.ColorMode.COLOR_MODE_DARK : ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT
      )
    } catch (_e) {
    }
  }

  private toggleSettings(): void {
    this.showSettings = !this.showSettings
  }

  private async logout(): Promise<void> {
    animateTo({ duration: 120, curve: Curve.EaseOut }, () => {
      this.logoutScale = 0.95
    })
    setTimeout(() => {
      animateTo({ duration: 200, curve: Curve.EaseOut }, () => {
        this.logoutScale = 1.0
      })
    }, 120)
    await sessionStore.clear()
    promptAction.showToast({ message: '已退出登录', duration: 1500 })
    router.replaceUrl({ url: 'pages/LoginPage' })
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 12 }) {
          Stack() {
            Circle()
              .width(72)
              .height(72)
              .fill($r('app.color.avatar_bg'))
            Circle()
              .width(64)
              .height(64)
              .linearGradient({
                angle: 135,
                colors: [[$r('app.color.avatar_gradient_start'), 0.0], [$r('app.color.avatar_gradient_end'), 1.0]]
              })
            Text(this.userName.substring(0, 1))
              .fontSize(26)
              .fontWeight(FontWeight.Bold)
              .fontColor(Color.White)
          }
          .width(72)
          .height(72)
          Text(this.userName)
            .fontSize($r('app.float.subtitle_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_on_dark'))
          Text(this.role)
            .fontSize(11)
            .fontColor($r('app.color.text_muted'))
            .padding({ left: 10, right: 10, top: 3, bottom: 3 })
            .backgroundColor($r('app.color.settings_bg'))
            .borderRadius(8)
        }
        .width('100%')
        .padding({ top: 36, bottom: 20 })
        .alignItems(HorizontalAlign.Center)
        .opacity(this.contentOpacity)
        .translate({ y: this.contentOffsetY })
        .animation({ duration: 500, curve: Curve.EaseOut })

        Column({ space: 0 }) {
          this.ProfileRow('手机号', this.phone)
          Column()
            .width('100%')
            .height(1)
            .backgroundColor($r('app.color.divider_dark'))
            .margin({ left: 16 })
          Row() {
            Text($r('app.string.profile_settings'))
              .fontSize($r('app.float.body_font_size'))
              .fontColor($r('app.color.text_on_card'))
            Blank()
            Text('›')
              .fontSize(18)
              .fontColor($r('app.color.text_faint'))
              .margin({ left: 6 })
          }
          .width('100%')
          .height(52)
          .padding({ left: 16, right: 16 })
          .onClick(() => this.toggleSettings())
          if (this.showSettings) {
            Column({ space: 0 }) {
              this.SettingsToggleRow('接收推送通知', this.pushEnabled)
              Column()
                .width('100%')
                .height(1)
                .backgroundColor($r('app.color.divider_dark'))
                .margin({ left: 32 })
              this.ThemeToggleRow()
              Column()
                .width('100%')
                .height(1)
                .backgroundColor($r('app.color.divider_dark'))
                .margin({ left: 32 })
              this.SettingsToggleRow('清除缓存', false)
            }
            .transition({ type: TransitionType.Insert, opacity: 0, translate: { y: -10 } })
            .animation({ duration: 250, curve: Curve.EaseOut })
          }
          Column()
            .width('100%')
            .height(1)
            .backgroundColor($r('app.color.divider_dark'))
            .margin({ left: 16 })
          Row() {
            Text($r('app.string.profile_about'))
              .fontSize($r('app.float.body_font_size'))
              .fontColor($r('app.color.text_on_card'))
            Blank()
            Text('›')
              .fontSize(18)
              .fontColor($r('app.color.text_faint'))
              .margin({ left: 6 })
          }
          .width('100%')
          .height(52)
          .padding({ left: 16, right: 16 })
          .onClick(() => {
              promptAction.showDialog({
                title: '关于',
                message: '车载安全监测 v1.0.0\nBuild 2026\n© 2026 Safety Monitor',
                buttons: [{ text: '确定', color: '#3B82F6' }]
              })
            })
        }
        .width('100%')
        .padding(0)
        .backgroundColor($r('app.color.settings_bg'))
        .borderRadius(16)
        .border({ width: 1, color: $r('app.color.card_border') })
        .shadow({ radius: 8, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 2 })
        .margin({ left: 16, right: 16 })
        .opacity(this.contentOpacity)
        .translate({ y: this.contentOffsetY })
        .animation({ duration: 500, curve: Curve.EaseOut, delay: 100 })

        Column() {
          Row() {
            Text($r('app.string.profile_logout'))
              .fontSize($r('app.float.body_font_size'))
              .fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.danger'))
          }
          .width('100%')
          .height(52)
          .justifyContent(FlexAlign.Center)
          .backgroundColor($r('app.color.danger_bg'))
          .borderRadius(16)
          .border({ width: 1, color: $r('app.color.danger_border') })
          .shadow({ radius: 8, color: $r('app.color.danger_shadow'), offsetX: 0, offsetY: 2 })
          .scale({ x: this.logoutScale, y: this.logoutScale })
          .animation({ duration: 200, curve: Curve.EaseOut })
          .onClick(() => this.logout())
        }
        .padding({ left: 16, right: 16 })
        .opacity(this.contentOpacity)
        .translate({ y: this.contentOffsetY })
        .animation({ duration: 500, curve: Curve.EaseOut, delay: 200 })
      }
    }
    .scrollBar(BarState.Off)
    .edgeEffect(EdgeEffect.Spring)
    .width('100%')
    .height('100%')
  }

  @Builder
  ProfileRow(label: ResourceStr, value: string) {
    Row() {
      Text(label)
        .fontSize($r('app.float.body_font_size'))
        .fontColor($r('app.color.text_on_card'))
      Blank()
      if (value.length > 0) {
        Text(value)
          .fontSize($r('app.float.body_font_size'))
          .fontColor($r('app.color.text_muted'))
      }
      Text('›')
        .fontSize(18)
        .fontColor($r('app.color.text_faint'))
        .margin({ left: 6 })
    }
    .width('100%')
    .height(52)
    .padding({ left: 16, right: 16 })
  }

  @Builder
  @Builder
  ThemeToggleRow() {
    Row() {
      Text('夜间模式')
        .fontSize($r('app.float.body_font_size'))
        .fontColor($r('app.color.text_on_card'))
      Blank()
      Toggle({ type: ToggleType.Switch, isOn: this.isDarkMode })
        .onChange((isOn: boolean) => {
          this.toggleTheme(isOn)
        })
    }
    .width('100%')
    .height(48)
    .padding({ left: 32, right: 16 })
  }

  @Builder
  SettingsToggleRow(label: string, enabled: boolean) {
    Row() {
      Text(label)
        .fontSize($r('app.float.body_font_size'))
        .fontColor($r('app.color.text_on_card'))
      Blank()
      Row() {
        Circle()
          .width(14)
          .height(14)
          .fill(enabled ? $r('app.color.toggle_on') : $r('app.color.toggle_off'))
          .margin({ right: 2 })
        Text(enabled ? '已开启' : '未开启')
          .fontSize(11)
          .fontColor($r('app.color.text_muted'))
      }
    }
    .width('100%')
    .height(48)
    .padding({ left: 32, right: 16 })
    .onClick(() => {
      promptAction.showToast({ message: `${label} -- 已${enabled ? '' : '未'}开启`, duration: 1000 })
    })
  }
}
